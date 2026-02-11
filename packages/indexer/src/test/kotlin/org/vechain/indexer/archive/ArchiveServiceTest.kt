package org.vechain.indexer.archive

import com.mongodb.client.MongoCollection
import com.mongodb.client.result.InsertManyResult
import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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

@ExtendWith(MockKExtension::class)
internal class ArchiveServiceTest {
    @MockK private lateinit var mongoTemplate: MongoTemplate

    private lateinit var archiveService: ArchiveService<TestVersionedDocument>

    private val collectionName = "testVersionedDocument"

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { mongoTemplate.getCollectionName(TestVersionedDocument::class.java) } returns
            collectionName

        archiveService =
            ArchiveService(mongoTemplate, TestVersionedDocument::class.java, queryLimit = 100)
    }

    @Test
    fun `saveAll - empty list should return immediately`() {
        archiveService.saveAll(emptyList())

        verify(exactly = 0) { mongoTemplate.getCollection(any()) }
    }

    @Test
    fun `saveAll - non-empty list should save documents`() {
        val documents = listOf(TestVersionedDocument(), TestVersionedDocument())

        val mongoCollection = mockk<MongoCollection<Document>>()
        every { mongoTemplate.getCollection(collectionName) } returns mongoCollection
        every { mongoTemplate.converter } returns
            mockk {
                every { write(any(), any<Document>()) } answers
                    {
                        // Simulate writing the document fields into the bson doc
                        val doc = secondArg<Document>()
                        doc["version"] = 1
                        doc["blockNumber"] = 1L
                    }
            }
        every { mongoCollection.insertMany(any()) } returns mockk<InsertManyResult>()

        archiveService.saveAll(documents)

        val slot = slot<List<Document>>()
        verify(exactly = 1) { mongoCollection.insertMany(capture(slot)) }

        expect {
            that(slot.captured).hasSize(2)
            that(slot.captured[0].getString("_id"))
                .isEqualTo(buildArchiveId(documents[0], documents[0].version))
            that(slot.captured[1].getString("_id"))
                .isEqualTo(buildArchiveId(documents[1], documents[1].version))
            that(slot.captured[0].getBoolean("_isArchive")).isEqualTo(true)
            that(slot.captured[1].getBoolean("_isArchive")).isEqualTo(true)
            that(slot.captured[0].getString("_originalDocId")).isEqualTo("test-id")
            that(slot.captured[1].getString("_originalDocId")).isEqualTo("test-id")
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
