package org.vechain.indexer

import com.mongodb.client.MongoCollection
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.Document
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
        saveVersionedDocuments(
            emptyList<TestDocument>(),
            emptyList(),
            mongoTemplate,
            BLOCK_WINDOW,
            MAX_VERSIONS,
        )

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

        every { mongoTemplate.getCollectionName(TestDocument::class.java) } returns "test_documents"
        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        saveVersionedDocuments(updated, emptyList(), mongoTemplate, BLOCK_WINDOW, MAX_VERSIONS)

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

        every { mongoTemplate.getCollectionName(TestDocument::class.java) } returns "test_documents"
        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        saveVersionedDocuments(updated, existing, mongoTemplate, BLOCK_WINDOW, MAX_VERSIONS)

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

        every { mongoTemplate.getCollectionName(TestDocument::class.java) } returns "test_documents"
        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs
        every {
            collection.bulkWrite(
                any<List<com.mongodb.client.model.WriteModel<Document>>>(),
                any<com.mongodb.client.model.BulkWriteOptions>(),
            )
        } throws RuntimeException("write error")

        assertThrows<RuntimeException> {
            saveVersionedDocuments(updated, existing, mongoTemplate, BLOCK_WINDOW, MAX_VERSIONS)
        }

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

        every { mongoTemplate.getCollectionName(TestDocument::class.java) } returns "test_documents"
        every { mongoTemplate.getCollection("test_documents") } returns collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        val exception =
            assertThrows<VersionedDocumentInvariantException> {
                saveVersionedDocuments(
                    updated,
                    emptyList(),
                    mongoTemplate,
                    BLOCK_WINDOW,
                    MAX_VERSIONS,
                )
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

        every { mongoTemplate.getCollectionName(ZeroBasedTestDocument::class.java) } returns
            IndexerNames.ACCOUNT_OVERVIEW.COLLECTION
        every { mongoTemplate.getCollection(IndexerNames.ACCOUNT_OVERVIEW.COLLECTION) } returns
            collection
        every { mongoTemplate.converter } returns converter
        every { converter.write(any(), any<Document>()) } just Runs

        saveVersionedDocuments(updated, emptyList(), mongoTemplate, BLOCK_WINDOW, MAX_VERSIONS)

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
}
