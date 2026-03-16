package org.vechain.indexer

import com.mongodb.MongoClientSettings
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.UpdateOneModel
import com.mongodb.client.model.WriteModel
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.Document
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
    fun `preserves every existing version for the same document id in update pipeline order`() {
        val writes = slot<List<WriteModel<Document>>>()
        val updated =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 3,
                    blockNumber = 30,
                    blockId = "b3",
                    blockTimestamp = 3000,
                    state = "current",
                )
            )
        val existing =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 10,
                    blockId = "b1",
                    blockTimestamp = 1000,
                    state = "old",
                ),
                TestDocument(
                    id = "doc-1",
                    version = 2,
                    blockNumber = 20,
                    blockId = "b2",
                    blockTimestamp = 2000,
                    state = "middle",
                ),
            )

        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any<TestDocument>(), any<Document>()) } answers
            {
                val source = firstArg<TestDocument>()
                val target = secondArg<Document>()
                target["_id"] = source.getDocumentId()
                target["version"] = source.version
                target["blockNumber"] = source.blockNumber
                target["blockId"] = source.blockId
                target["blockTimestamp"] = source.blockTimestamp
                target["state"] = source.state
            }
        every {
            collection.bulkWrite(capture(writes), any<com.mongodb.client.model.BulkWriteOptions>())
        } returns mockk(relaxed = true)

        persist(updated, existing, "test_documents")

        val updateModel = writes.captured.single() as UpdateOneModel<Document>
        val pipeline =
            updateModel.updatePipeline!!.map {
                it.toBsonDocument(
                    Document::class.java,
                    MongoClientSettings.getDefaultCodecRegistry(),
                )
            }
        val pipelineText = pipeline.joinToString("\n") { it.toJson() }

        assertTrue(pipelineText.contains("\"state\": \"current\""))
        assertTrue(pipelineText.contains("\"state\": \"middle\""))
        assertTrue(pipelineText.contains("\"state\": \"old\""))
        assertTrue(
            pipelineText.indexOf("\"state\": \"middle\"") <
                pipelineText.indexOf("\"state\": \"old\"")
        )
        assertEquals(1, writes.captured.size)
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

    private data class TestDocument(
        private val id: String,
        override val version: Int,
        override val blockNumber: Long,
        override val blockId: String,
        override val blockTimestamp: Long,
        val state: String = id,
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
}
