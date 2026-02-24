package org.vechain.indexer.archive

import com.mongodb.MongoBulkWriteException
import com.mongodb.bulk.BulkWriteError
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.BsonDocument
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.BulkOperationException
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.buildArchiveId
import strikt.api.expect
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

class TestVersionedDocument : VersionedDocument {
    override var version: Int = 1
    override val blockId: String = "test-block-id"

    override fun getDocumentId(): String = "test-id"

    override var blockNumber: Long = 1
    override val blockTimestamp: Long = System.currentTimeMillis()
}

class TestArchive(override val data: TestVersionedDocument) : Archive<TestVersionedDocument> {
    override var id: String = buildArchiveId(data, data.version)

    constructor(id: String, data: TestVersionedDocument) : this(data) {
        this.id = id
    }
}

@ExtendWith(MockKExtension::class)
internal class ArchiveServiceTest {
    @MockK private lateinit var mongoTemplate: MongoTemplate
    @MockK private lateinit var bulkOps: BulkOperations

    private lateinit var archiveService: ArchiveService<TestVersionedDocument, TestArchive>

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        archiveService =
            ArchiveService(
                mongoTemplate,
                TestVersionedDocument::class.java,
                TestArchive::class.java,
                queryLimit = 100,
            )
    }

    @Test
    fun `saveAll - empty list should return immediately`() {
        archiveService.saveAll(emptyList())

        verify(exactly = 0) { mongoTemplate.bulkOps(any(), TestArchive::class.java) }
    }

    @Test
    fun `saveAll - non-empty list should save documents`() {
        val documents = listOf(TestVersionedDocument(), TestVersionedDocument())
        val archives = documents.map { TestArchive(buildArchiveId(it, it.version), it) }
        val slot = slot<List<TestArchive>>()

        every {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        } returns bulkOps
        every { bulkOps.insert(capture(slot)) } returns bulkOps
        every { bulkOps.execute() } returns mockk()

        archiveService.saveAll(documents)

        verify(exactly = 1) {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        }
        verify(exactly = 1) { bulkOps.insert(any<List<TestArchive>>()) }
        verify(exactly = 1) { bulkOps.execute() }

        expect {
            that(slot.captured).hasSize(2)
            that(slot.captured[0].id).isEqualTo(archives[0].id)
            that(slot.captured[1].id).isEqualTo(archives[1].id)
        }
    }

    @Test
    fun `saveAll - duplicate key errors are silently skipped`() {
        val documents = listOf(TestVersionedDocument())
        val duplicateError = BulkWriteError(11000, "duplicate key", BsonDocument(), 0)
        val bulkOperationException = buildBulkOperationException(listOf(duplicateError))

        every {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        } returns bulkOps
        every { bulkOps.insert(any<List<TestArchive>>()) } returns bulkOps
        every { bulkOps.execute() } throws bulkOperationException

        archiveService.saveAll(documents)
    }

    @Test
    fun `saveAll - non-duplicate errors are re-thrown`() {
        val documents = listOf(TestVersionedDocument())
        val otherError = BulkWriteError(12345, "some other error", BsonDocument(), 0)
        val bulkOperationException = buildBulkOperationException(listOf(otherError))

        every {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        } returns bulkOps
        every { bulkOps.insert(any<List<TestArchive>>()) } returns bulkOps
        every { bulkOps.execute() } throws bulkOperationException

        assertThrows<BulkOperationException> { archiveService.saveAll(documents) }
    }

    @Test
    fun `saveAll - mixed errors with any non-duplicate are re-thrown`() {
        val documents = listOf(TestVersionedDocument())
        val duplicateError = BulkWriteError(11000, "duplicate key", BsonDocument(), 0)
        val otherError = BulkWriteError(12345, "some other error", BsonDocument(), 1)
        val bulkOperationException = buildBulkOperationException(listOf(duplicateError, otherError))

        every {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        } returns bulkOps
        every { bulkOps.insert(any<List<TestArchive>>()) } returns bulkOps
        every { bulkOps.execute() } throws bulkOperationException

        assertThrows<BulkOperationException> { archiveService.saveAll(documents) }
    }

    private fun buildBulkOperationException(errors: List<BulkWriteError>): BulkOperationException {
        val mongoBulkWriteException = mockk<MongoBulkWriteException>()
        every { mongoBulkWriteException.writeErrors } returns errors
        every { mongoBulkWriteException.writeResult } returns mockk()
        return BulkOperationException("Bulk write error", mongoBulkWriteException)
    }

    @Test
    fun `rollback - no documents to rollback`() {
        val blockNumber = 1L
        every { mongoTemplate.find(any<Query>(), TestVersionedDocument::class.java) } returns
            emptyList()

        archiveService.rollback(blockNumber)

        verify(exactly = 1) { mongoTemplate.find(any<Query>(), TestVersionedDocument::class.java) }
        verify(exactly = 0) { mongoTemplate.bulkOps(any(), TestVersionedDocument::class.java) }
    }
}
