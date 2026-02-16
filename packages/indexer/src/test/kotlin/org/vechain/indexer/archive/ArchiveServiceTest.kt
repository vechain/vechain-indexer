package org.vechain.indexer.archive

import com.mongodb.client.MongoCollection
import com.mongodb.client.model.InsertManyOptions
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.VersionedDocument
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

@ExtendWith(MockKExtension::class)
internal class ArchiveServiceTest {
    @MockK private lateinit var mongoTemplate: MongoTemplate

    @MockK private lateinit var mongoConverter: MongoConverter

    @MockK private lateinit var mongoCollection: MongoCollection<Document>

    private lateinit var archiveService: ArchiveService<TestVersionedDocument>

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { mongoTemplate.getCollectionName(TestVersionedDocument::class.java) } returns
            "test_collection"
        every { mongoTemplate.converter } returns mongoConverter

        archiveService =
            ArchiveService(mongoTemplate, TestVersionedDocument::class.java, queryLimit = 100)
    }

    @Test
    fun `saveAll - empty list should return immediately`() {
        archiveService.saveAll(emptyList())

        verify(exactly = 0) { mongoTemplate.getCollection(any()) }
    }

    @Test
    fun `saveAll - non-empty list should save documents via raw driver`() {
        val documents = listOf(TestVersionedDocument(), TestVersionedDocument())
        val capturedDocs = slot<List<Document>>()

        every { mongoTemplate.getCollection("test_collection") } returns mongoCollection
        every { mongoConverter.write(any(), any<Document>()) } answers
            {
                val doc = secondArg<Document>()
                doc["_id"] = "test-id"
                doc["blockNumber"] = 1L
                doc["version"] = 1
            }
        every {
            mongoCollection.insertMany(capture(capturedDocs), any<InsertManyOptions>())
        } returns mockk()

        archiveService.saveAll(documents)

        verify(exactly = 1) {
            mongoCollection.insertMany(any<List<Document>>(), any<InsertManyOptions>())
        }

        expect {
            that(capturedDocs.captured).hasSize(2)
            that(capturedDocs.captured[0]["_isArchive"]).isEqualTo(true)
            that(capturedDocs.captured[0]["_originalDocId"]).isEqualTo("test-id")
        }
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
