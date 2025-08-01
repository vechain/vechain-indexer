package org.vechain.indexer.archive

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Query
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.utils.IdUtils
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
    override var id: String = IdUtils.buildArchiveId(data, data.version)

    constructor(id: String, data: TestVersionedDocument) : this(data) {
        this.id = id
    }
}

@ExtendWith(MockKExtension::class)
internal class ArchiveServiceTest {
    @MockK private lateinit var mongoTemplate: MongoTemplate

    private lateinit var archiveService: ArchiveService<TestVersionedDocument, TestArchive>

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        archiveService =
            ArchiveService(
                mongoTemplate,
                TestVersionedDocument::class.java,
                TestArchive::class.java,
            )
    }

    @Test
    fun `saveAll - empty list should return immediately`() {
        archiveService.saveAll(emptyList())

        verify(exactly = 0) {
            mongoTemplate.insert(any<List<TestArchive>>(), TestArchive::class.java)
        }
    }

    @Test
    fun `saveAll - non-empty list should save documents`() {
        val documents = listOf(TestVersionedDocument(), TestVersionedDocument())
        val archives = documents.map { TestArchive(IdUtils.buildArchiveId(it, it.version), it) }
        val slot = slot<List<TestArchive>>()

        every { mongoTemplate.insert(capture(slot), TestArchive::class.java) } returns
            listOf<TestArchive>()

        archiveService.saveAll(documents)

        verify(exactly = 1) {
            mongoTemplate.insert(any<List<TestArchive>>(), TestArchive::class.java)
        }

        expect {
            that(slot.captured).hasSize(2)
            that(slot.captured[0].id).isEqualTo(archives[0].id)
            that(slot.captured[1].id).isEqualTo(archives[1].id)
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
