package org.vechain.indexer

import com.mongodb.client.MongoCollection
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.Document
import org.bson.conversions.Bson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter

@ExtendWith(MockKExtension::class)
internal class VersionedDocumentPersistenceTest {

    @MockK(relaxed = true) private lateinit var mongoTemplate: MongoTemplate

    @MockK(relaxed = true) private lateinit var converter: MongoConverter

    @MockK(relaxed = true) private lateinit var collection: MongoCollection<Document>

    companion object {
        private const val BLOCK_WINDOW = 10000L
        private const val MAX_VERSIONS = 100
    }

    @Test
    fun `does nothing when updated list is empty`() {
        persist(emptyList<TestDocument>(), emptyList(), "test_documents")

        verify { mongoTemplate wasNot Called }
    }

    @Test
    fun `saves only updated documents when no existing records`() {
        val updated =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 10,
                    blockId = "b1",
                    blockTimestamp = 1000,
                )
            )

        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        persist(updated, emptyList(), "test_documents")

        verify(exactly = 1) {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        }
    }

    @Test
    fun `performs bulk write with versioning when existing documents present`() {
        val updated =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 2,
                    blockNumber = 20,
                    blockId = "b1",
                    blockTimestamp = 1000,
                )
            )
        val existing =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 15,
                    blockId = "b0",
                    blockTimestamp = 900,
                )
            )

        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        persist(updated, existing, "test_documents")

        verify(exactly = 1) {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        }
    }

    @Test
    fun `propagates exception when bulk write fails`() {
        val updated =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 2,
                    blockNumber = 20,
                    blockId = "b1",
                    blockTimestamp = 1000,
                )
            )
        val existing =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 15,
                    blockId = "b0",
                    blockTimestamp = 900,
                )
            )

        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs
        every {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        } throws RuntimeException("write error")

        assertThrows<RuntimeException> { persist(updated, existing, "test_documents") }

        verify(exactly = 1) {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        }
    }

    @Test
    fun `fails fast when non-initial version is missing existing document`() {
        val updated =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 2,
                    blockNumber = 20,
                    blockId = "b1",
                    blockTimestamp = 1000,
                )
            )

        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        val exception =
            assertThrows<VersionedDocumentInvariantException> {
                persist(updated, emptyList(), "test_documents")
            }

        assertTrue(exception.message!!.contains("test_documents/doc-1"))
        verify(exactly = 0) {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        }
    }

    @Test
    fun `uses standard initial version for configured collections`() {
        val updated =
            listOf(
                ZeroBasedTestDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 20,
                    blockId = "b1",
                    blockTimestamp = 1000,
                )
            )

        every { mongoTemplate.getCollection(IndexerNames.ACCOUNT_OVERVIEW.COLLECTION) } returns
            collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        persist(updated, emptyList(), IndexerNames.ACCOUNT_OVERVIEW.COLLECTION)

        verify(exactly = 1) {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        }
    }

    @Test
    fun `unsets fields omitted from updated document when existing document has them`() {
        val writes = slot<List<com.mongodb.client.model.WriteModel<Document>>>()
        val updated =
            listOf(
                NullableFieldDocument(
                    id = "doc-1",
                    version = 2,
                    blockNumber = 20,
                    blockId = "b1",
                    blockTimestamp = 1000,
                    retained = "current",
                    optional = null,
                )
            )
        val existing =
            listOf(
                NullableFieldDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 15,
                    blockId = "b0",
                    blockTimestamp = 900,
                    retained = "previous",
                    optional = "stale-value",
                )
            )

        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } answers
            {
                val source = firstArg<NullableFieldDocument>()
                val target = secondArg<Document>()
                target["_id"] = source.getDocumentId()
                target["version"] = source.version
                target["blockNumber"] = source.blockNumber
                target["blockId"] = source.blockId
                target["blockTimestamp"] = source.blockTimestamp
                target["retained"] = source.retained
                source.optional?.let { target["optional"] = it }
            }
        every {
            collection.bulkWrite(capture(writes), any<com.mongodb.client.model.BulkWriteOptions>())
        } returns mockk(relaxed = true)

        persist(updated, existing, "test_documents")

        val update = writes.captured.single() as com.mongodb.client.model.UpdateOneModel<Document>
        val pipeline = requireNotNull(update.updatePipeline).map { it.asDocument() }
        val previousVersionsStage =
            pipeline[1].getEmbedded(
                listOf("\$set", InlineVersionService.PREVIOUS_VERSIONS_FIELD),
                Document::class.java,
            )
        val previousVersions = previousVersionsStage["\$concatArrays"] as List<*>
        val archivedVersion = (previousVersions.first() as List<*>).single() as Document

        assertEquals(listOf("optional"), pipeline[0]["\$unset"])
        assertEquals(
            "current",
            pipeline[1].getEmbedded(listOf("\$set", "retained"), String::class.java),
        )
        assertEquals(2, pipeline[1].getEmbedded(listOf("\$set", "version"), Number::class.java))
        assertEquals("stale-value", archivedVersion["optional"])
    }

    private data class TestDocument(
        private val id: String,
        override val version: Int,
        override val blockNumber: Long,
        override val blockId: String,
        override val blockTimestamp: Long,
    ) : VersionedDocument {
        override fun getDocumentId(): String = id
    }

    private data class ZeroBasedTestDocument(
        private val id: String,
        override val version: Int,
        override val blockNumber: Long,
        override val blockId: String,
        override val blockTimestamp: Long,
    ) : VersionedDocument {
        override fun getDocumentId(): String = id
    }

    private data class NullableFieldDocument(
        private val id: String,
        override val version: Int,
        override val blockNumber: Long,
        override val blockId: String,
        override val blockTimestamp: Long,
        val retained: String,
        val optional: String?,
    ) : VersionedDocument {
        override fun getDocumentId(): String = id
    }

    private inline fun <reified T : VersionedDocument> persist(
        updated: List<T>,
        existing: List<T>,
        collectionName: String,
    ) {
        InlineVersionService.bulkUpsertWithVersions(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = BLOCK_WINDOW,
            maxVersions = MAX_VERSIONS,
            initialVersion = VersionedDocumentInitialVersions.forCollection(collectionName),
            collectionName = collectionName,
        )
    }

    private fun Bson.asDocument(): Document = this as Document
}
