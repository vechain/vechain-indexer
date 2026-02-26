package org.vechain.indexer.archive

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.buildArchiveId

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
    @MockK private lateinit var mongoConverter: MongoConverter

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
    fun `saveAll - non-empty list should upsert documents`() {
        val documents = listOf(TestVersionedDocument(), TestVersionedDocument())

        every {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        } returns bulkOps
        every { mongoTemplate.converter } returns mongoConverter
        every { mongoConverter.write(any(), any<Document>()) } answers
            {
                val doc = secondArg<Document>()
                doc["_id"] = "test-archive-id"
                doc["data"] = "test-data"
            }
        every { bulkOps.upsert(any<Query>(), any<Update>()) } returns bulkOps
        every { bulkOps.execute() } returns mockk()

        archiveService.saveAll(documents)

        verify(exactly = 1) {
            mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, TestArchive::class.java)
        }
        verify(exactly = 2) { bulkOps.upsert(any<Query>(), any<Update>()) }
        verify(exactly = 1) { bulkOps.execute() }
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
