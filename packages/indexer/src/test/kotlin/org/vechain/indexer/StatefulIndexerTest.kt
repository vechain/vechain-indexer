package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.repository.BaseIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.service.PrunerService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

@ExtendWith(MockKExtension::class)
internal class StatefulIndexerTest {
    @MockK lateinit var repository: BaseIndexedRepository<*>

    @MockK lateinit var thorClient: ThorClient

    @MockK
    lateinit var archiveService: ArchiveService<VersionedDocument, Archive<VersionedDocument>>

    @MockK lateinit var prunerService: PrunerService<VersionedDocument, Archive<VersionedDocument>>

    private lateinit var statefulIndexer:
        StatefulIndexer<VersionedDocument, Archive<VersionedDocument>>

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        statefulIndexer =
            spyk(
                object :
                    StatefulIndexer<VersionedDocument, Archive<VersionedDocument>>(
                        repository,
                        0L,
                        thorClient,
                        1000L,
                        10000,
                        archiveService,
                        prunerService,
                    ) {
                    override fun processBlock(block: Block) {
                        // do nothing
                    }
                },
            )
    }

    @Test
    fun `rollback should call archiveService rollback`() {
        val blockNumber = 100L

        every { archiveService.rollback(blockNumber) } just Runs

        statefulIndexer.rollback(blockNumber)

        verify(exactly = 1) { archiveService.rollback(blockNumber) }
    }

    @Test
    fun `runPruner should skip if not fully synced`() {
        every { statefulIndexer.status } returns Status.SYNCING

        every { prunerService.runPruner(any(), any(), any()) } just Runs
        statefulIndexer.runPruner()

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
    }

    @Test
    fun `runPruner should skip if not enough blocks to prune`() {
        every { statefulIndexer.status } returns Status.FULLY_SYNCED
        every { statefulIndexer.currentBlockNumber } returns 5000L
        every { prunerService.runPruner(any(), any(), any()) } just Runs
        statefulIndexer.runPruner()

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
    }

    @Test
    fun `runPruner should prune records in chunks`() {
        every { statefulIndexer.status } returns Status.FULLY_SYNCED
        every { statefulIndexer.currentBlockNumber } returns 20000L

        val records = List(25000) { mockk<VersionedDocument>(relaxed = true) }
        val recordIds = records.map { it.getDocumentId() }

        every { archiveService.findRecordsToPrune(10000L) } returns recordIds

        every { archiveService.removeAll(any()) } just Runs

        every { prunerService.runPruner(any(), any(), any()) } answers
            {
                archiveService.findRecordsToPrune(10000L)

                records.chunked(10000).forEach { chunk ->
                    archiveService.removeAll(chunk.map { it.getDocumentId() })
                }
            }

        statefulIndexer.runPruner()

        verify(exactly = 1) { archiveService.findRecordsToPrune(10000L) }

        verify(exactly = 3) { archiveService.removeAll(any()) }
    }
}
