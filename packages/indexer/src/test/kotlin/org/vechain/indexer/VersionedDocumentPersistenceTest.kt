package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.convert.MongoConverter
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.TargetedPruner

@ExtendWith(MockKExtension::class)
internal class VersionedDocumentPersistenceTest {

    @MockK private lateinit var archiveService: ArchiveService<TestDocument, TestArchive>

    @MockK(relaxed = true) private lateinit var pruner: TargetedPruner<TestDocument, TestArchive>

    @MockK(relaxed = true) private lateinit var mongoTemplate: MongoTemplate

    @MockK(relaxed = true) private lateinit var bulkOps: BulkOperations

    @MockK(relaxed = true) private lateinit var converter: MongoConverter

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

        every { archiveService.mongoTemplate } returns mongoTemplate
        every { mongoTemplate.bulkOps(any(), any<Class<*>>()) } returns bulkOps
        every { mongoTemplate.converter } returns converter
        every { archiveService.saveAll(any()) } just runs
        saveVersionedDocuments(updated, emptyList(), archiveService, pruner)

        verify(exactly = 1) { bulkOps.execute() }
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

        every { archiveService.mongoTemplate } returns mongoTemplate
        every { mongoTemplate.bulkOps(any(), any<Class<*>>()) } returns bulkOps
        every { mongoTemplate.converter } returns converter
        every { archiveService.saveAll(existing) } just runs

        saveVersionedDocuments(updated, existing, archiveService, pruner)

        verify(exactly = 1) { bulkOps.execute() }
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

        every { archiveService.mongoTemplate } returns mongoTemplate
        every { mongoTemplate.bulkOps(any(), any<Class<*>>()) } returns bulkOps
        every { mongoTemplate.converter } returns converter
        every { archiveService.saveAll(existing) } just runs
        saveVersionedDocuments(updated, existing, archiveService, pruner)

        verify(exactly = 1) { bulkOps.execute() }
        verify(exactly = 1) { archiveService.saveAll(existing) }
        verify(exactly = 1) { pruner.run(20, listOf("doc-2")) }
    }

    @Test
    fun `does not archive or prune when bulk upsert fails`() {
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

        every { archiveService.mongoTemplate } returns mongoTemplate
        every { mongoTemplate.bulkOps(any(), any<Class<*>>()) } returns bulkOps
        every { mongoTemplate.converter } returns converter
        every { bulkOps.execute() } throws RuntimeException("write error")

        assertThrows<RuntimeException> {
            saveVersionedDocuments(updated, existing, archiveService, pruner)
        }

        verify(exactly = 1) { bulkOps.execute() }
        verify(exactly = 0) { archiveService.saveAll(any()) }
        verify { pruner wasNot Called }
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
