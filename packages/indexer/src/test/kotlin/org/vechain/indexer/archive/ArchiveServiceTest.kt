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

    @MockK private lateinit var converter: MongoConverter

    @MockK private lateinit var collection: MongoCollection<Document>

    private lateinit var archiveService: ArchiveService<TestVersionedDocument>

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        every { mongoTemplate.getCollectionName(TestVersionedDocument::class.java) } returns
            "test_collection"
        every { mongoTemplate.getCollection("test_collection") } returns collection
        every { mongoTemplate.converter } returns converter

        archiveService =
            ArchiveService(mongoTemplate, TestVersionedDocument::class.java, queryLimit = 100)
    }

    @Test
    fun `saveAll - empty list should return immediately`() {
        archiveService.saveAll(emptyList())

        verify(exactly = 0) {
            collection.insertMany(any<List<Document>>(), any<InsertManyOptions>())
        }
    }

    @Test
    fun `saveAll - non-empty list should save documents`() {
        val documents = listOf(TestVersionedDocument(), TestVersionedDocument())
        val slot = slot<List<Document>>()

        every { converter.write(any(), any<Document>()) } just Runs
        every { collection.insertMany(capture(slot), any()) } returns mockk()

        archiveService.saveAll(documents)

        verify(exactly = 1) {
            collection.insertMany(any<List<Document>>(), any<InsertManyOptions>())
        }

        expect {
            that(slot.captured).hasSize(2)
            that(slot.captured[0].getString("_id"))
                .isEqualTo(buildArchiveId(documents[0], documents[0].version))
            that(slot.captured[0].getBoolean("_isArchive")).isEqualTo(true)
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
