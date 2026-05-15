package org.vechain.indexer

import com.mongodb.bulk.BulkWriteResult
import com.mongodb.client.FindIterable
import com.mongodb.client.MongoCollection
import com.mongodb.client.MongoCursor
import com.mongodb.client.model.BulkWriteOptions
import com.mongodb.client.model.DeleteOneModel
import com.mongodb.client.model.ReplaceOneModel
import com.mongodb.client.model.WriteModel
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import strikt.api.expectThat
import strikt.assertions.hasSize

@ExtendWith(MockKExtension::class)
internal class InlineVersionServiceTest {

    @MockK(relaxed = true) private lateinit var mongoTemplate: MongoTemplate

    @MockK(relaxed = true) private lateinit var collection: MongoCollection<Document>

    @MockK(relaxed = true) private lateinit var findIterable: FindIterable<Document>

    @MockK(relaxed = true) private lateinit var cursor: MongoCursor<Document>

    @Test
    fun `rollback restores previous version for one-based collections`() {
        val writes = slot<List<WriteModel<Document>>>()
        val doc =
            Document("_id", "doc-1")
                .append("version", 2)
                .append("blockNumber", 10L)
                .append(
                    "_previousVersions",
                    listOf(Document("version", 1).append("blockNumber", 9L).append("field", "old")),
                )

        every { mongoTemplate.getCollection("test_collection") } returns collection
        every { collection.find(any<Bson>()) } returns findIterable
        every { findIterable.iterator() } returns cursor
        every { cursor.hasNext() } returnsMany listOf(true, false)
        every { cursor.next() } returns doc
        every { collection.bulkWrite(capture(writes), any<BulkWriteOptions>()) } returns
            mockBulkWriteResult(modifiedCount = 1, deletedCount = 0)

        InlineVersionService.rollback("test_collection", 10L, mongoTemplate, initialVersion = 1)

        verify(exactly = 1) { collection.bulkWrite(any<List<WriteModel<Document>>>(), any()) }
        expectThat(writes.captured).hasSize(1)

        val write = writes.captured.single()
        assertInstanceOf(ReplaceOneModel::class.java, write)
        val replacement = (write as ReplaceOneModel<Document>).replacement
        assertEquals("doc-1", replacement["_id"])
        assertEquals(1, replacement["version"])
        assertEquals("old", replacement["field"])
        expectThat(replacement["_previousVersions"] as List<*>).hasSize(0)
    }

    @Test
    fun `rollback deletes one-based initial version documents`() {
        val writes = slot<List<WriteModel<Document>>>()
        val doc = Document("_id", "doc-1").append("version", 1).append("blockNumber", 10L)

        every { mongoTemplate.getCollection("test_collection") } returns collection
        every { collection.find(any<Bson>()) } returns findIterable
        every { findIterable.iterator() } returns cursor
        every { cursor.hasNext() } returnsMany listOf(true, false)
        every { cursor.next() } returns doc
        every { collection.bulkWrite(capture(writes), any<BulkWriteOptions>()) } returns
            mockBulkWriteResult(modifiedCount = 0, deletedCount = 1)

        InlineVersionService.rollback("test_collection", 10L, mongoTemplate, initialVersion = 1)

        verify(exactly = 1) { collection.bulkWrite(any<List<WriteModel<Document>>>(), any()) }
        expectThat(writes.captured).hasSize(1)
        assertInstanceOf(DeleteOneModel::class.java, writes.captured.single())
    }

    @Test
    fun `rollback walks past retained versions that are still inside the rolled-back range`() {
        // Regression: a doc updated frequently has _previousVersions whose newest entries are
        // themselves still >= the rollback target. The pre-fix behaviour restored
        // _previousVersions[0] unconditionally, leaving the doc at a blockNumber inside the
        // rolled-back range and tripping alignToBlock's lastSyncedBlock check on the next pass.
        val writes = slot<List<WriteModel<Document>>>()
        val doc =
            Document("_id", "doc-1")
                .append("version", 4)
                .append("blockNumber", 400L)
                .append("field", "v4")
                .append(
                    "_previousVersions",
                    listOf(
                        Document("version", 3).append("blockNumber", 300L).append("field", "v3"),
                        Document("version", 2).append("blockNumber", 200L).append("field", "v2"),
                        Document("version", 1).append("blockNumber", 100L).append("field", "v1"),
                    ),
                )

        every { mongoTemplate.getCollection("test_collection") } returns collection
        every { collection.find(any<Bson>()) } returns findIterable
        every { findIterable.iterator() } returns cursor
        every { cursor.hasNext() } returnsMany listOf(true, false)
        every { cursor.next() } returns doc
        every { collection.bulkWrite(capture(writes), any<BulkWriteOptions>()) } returns
            mockBulkWriteResult(modifiedCount = 1, deletedCount = 0)

        InlineVersionService.rollback("test_collection", 250L, mongoTemplate, initialVersion = 1)

        val write = writes.captured.single()
        assertInstanceOf(ReplaceOneModel::class.java, write)
        val replacement = (write as ReplaceOneModel<Document>).replacement
        assertEquals(2, replacement["version"])
        assertEquals(200L, replacement["blockNumber"])
        assertEquals("v2", replacement["field"])
        // The skipped v3 entry must NOT survive in _previousVersions — only entries older than
        // the restored snapshot remain.
        val remaining =
            @Suppress("UNCHECKED_CAST") (replacement["_previousVersions"] as List<Document>)
        expectThat(remaining).hasSize(1)
        assertEquals(1, remaining.single()["version"])
        assertEquals(100L, remaining.single()["blockNumber"])
    }

    @Test
    fun `rollback throws when every retained version is inside the rolled-back range`() {
        // Every retained snapshot has blockNumber >= target, so no pre-target state is
        // representable. The operator needs to know — drop state for this indexer and restart.
        val doc =
            Document("_id", "doc-1")
                .append("version", 4)
                .append("blockNumber", 400L)
                .append(
                    "_previousVersions",
                    listOf(
                        Document("version", 3).append("blockNumber", 390L),
                        Document("version", 2).append("blockNumber", 380L),
                        Document("version", 1).append("blockNumber", 370L),
                    ),
                )

        every { mongoTemplate.getCollection("test_collection") } returns collection
        every { collection.find(any<Bson>()) } returns findIterable
        every { findIterable.iterator() } returns cursor
        every { cursor.hasNext() } returnsMany listOf(true, false)
        every { cursor.next() } returns doc

        assertThrows(RollbackException::class.java) {
            InlineVersionService.rollback(
                "test_collection",
                250L,
                mongoTemplate,
                initialVersion = 1,
            )
        }

        verify(exactly = 0) { collection.bulkWrite(any<List<WriteModel<Document>>>(), any()) }
    }

    private fun mockBulkWriteResult(modifiedCount: Int, deletedCount: Int): BulkWriteResult =
        mockk {
            every { this@mockk.modifiedCount } returns modifiedCount
            every { this@mockk.deletedCount } returns deletedCount
        }
}
