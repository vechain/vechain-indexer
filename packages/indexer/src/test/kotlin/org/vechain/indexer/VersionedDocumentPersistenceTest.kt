package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.CrudRepository
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.TargetedPruner

@ExtendWith(MockKExtension::class)
internal class VersionedDocumentPersistenceTest {

    @MockK private lateinit var repository: CrudRepository<TestDocument, String>

    @MockK private lateinit var archiveService: ArchiveService<TestDocument, TestArchive>

    @MockK(relaxed = true) private lateinit var pruner: TargetedPruner<TestDocument, TestArchive>

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

        every { repository.saveAll(updated) } returns updated
        every { archiveService.saveAll(any()) } just runs
        saveVersionedDocuments(updated, emptyList(), repository, archiveService, pruner)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 0) { archiveService.saveAll(any()) }
        verify { pruner wasNot Called }
    }

    @Test
    fun `archives existing documents without pruning when no older versions`() {
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
        val existing =
            listOf(
                TestDocument(
                    id = "doc-2",
                    version = 1,
                    blockNumber = 8,
                    blockId = "b2",
                    blockTimestamp = 900,
                )
            )

        every { repository.saveAll(updated) } returns updated
        every { archiveService.saveAll(existing) } just runs

        saveVersionedDocuments(updated, existing, repository, archiveService, pruner)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { archiveService.saveAll(existing) }
        verify { pruner wasNot Called }
    }

    @Test
    fun `prunes older archives when previous versions exist`() {
        val updated =
            listOf(
                TestDocument(
                    id = "doc-1",
                    version = 1,
                    blockNumber = 20,
                    blockId = "b1",
                    blockTimestamp = 1000,
                )
            )
        val existing =
            listOf(
                TestDocument(
                    id = "doc-2",
                    version = 2,
                    blockNumber = 15,
                    blockId = "b2",
                    blockTimestamp = 900,
                )
            )

        every { repository.saveAll(updated) } returns updated
        every { archiveService.saveAll(existing) } just runs
        saveVersionedDocuments(updated, existing, repository, archiveService, pruner)

        verify(exactly = 1) { repository.saveAll(updated) }
        verify(exactly = 1) { archiveService.saveAll(existing) }
        verify(exactly = 1) { pruner.run(20, listOf("doc-2")) }
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

    private data class TestArchive(override val id: String, override val data: TestDocument) :
        Archive<TestDocument>
}
