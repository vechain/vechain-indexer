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
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@ExtendWith(MockKExtension::class)
internal class StatefulLogsIndexerTest {
    @MockK lateinit var repository: BaseIndexedRepository<*>

    @MockK lateinit var thorClient: ThorClient

    @MockK
    lateinit var archiveService: ArchiveService<VersionedDocument, Archive<VersionedDocument>>

    @MockK lateinit var prunerService: PrunerService<VersionedDocument, Archive<VersionedDocument>>

    private lateinit var statefulLogsIndexer:
        StatefulLogsIndexer<VersionedDocument, Archive<VersionedDocument>>

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        statefulLogsIndexer =
            spyk(
                object :
                    StatefulLogsIndexer<VersionedDocument, Archive<VersionedDocument>>(
                        repository,
                        0L,
                        thorClient,
                        1000L,
                        10000,
                        logsType = setOf(LogType.EVENT),
                        null,
                        null,
                        archiveService,
                        prunerService,
                    ) {
                    override fun processLogs(
                        events: List<EventLog>,
                        transfers: List<TransferLog>,
                    ) {
                        // do nothing
                    }
                },
            )
    }

    @Test
    fun `rollback should call archiveService rollback`() {
        val blockNumber = 100L

        every { archiveService.rollback(blockNumber) } just Runs

        statefulLogsIndexer.rollback(blockNumber)

        verify(exactly = 1) { archiveService.rollback(blockNumber) }
    }

    @Test
    fun `runPruner should skip if not fully synced`() {
        every { statefulLogsIndexer.status } returns Status.SYNCING

        every { prunerService.runPruner(any(), any(), any()) } just Runs
        statefulLogsIndexer.runPruner()

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
    }

    @Test
    fun `runPruner should skip if not enough blocks to prune`() {
        every { statefulLogsIndexer.status } returns Status.FULLY_SYNCED
        every { statefulLogsIndexer.currentBlockNumber } returns 5000L
        every { prunerService.runPruner(any(), any(), any()) } just Runs
        statefulLogsIndexer.runPruner()

        verify(exactly = 0) { archiveService.findRecordsToPrune(any()) }
    }

    @Test
    fun `runPruner should prune records in chunks`() {
        every { statefulLogsIndexer.status } returns Status.FULLY_SYNCED
        every { statefulLogsIndexer.currentBlockNumber } returns 20000L

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

        statefulLogsIndexer.runPruner()

        verify(exactly = 1) { archiveService.findRecordsToPrune(10000L) }

        verify(exactly = 3) { archiveService.removeAll(any()) }
    }
}
