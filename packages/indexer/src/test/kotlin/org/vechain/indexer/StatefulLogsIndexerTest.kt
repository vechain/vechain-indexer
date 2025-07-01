package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.model.Archive
import org.vechain.indexer.model.VersionedDocument
import org.vechain.indexer.pruner.Pruner
import org.vechain.indexer.repository.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.service.ArchiveService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.enums.LogType
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.thor.model.TransferLog

@ExtendWith(MockKExtension::class)
internal class StatefulLogsIndexerTest {
    @MockK lateinit var repository: BasePagingAndSortingIndexedRepository<*, *>

    @MockK lateinit var thorClient: ThorClient

    @MockK
    lateinit var archiveService: ArchiveService<VersionedDocument, Archive<VersionedDocument>>

    @MockK lateinit var pruner: Pruner<VersionedDocument, Archive<VersionedDocument>>

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
                        pruner,
                    ) {
                    override fun processLogs(events: List<EventLog>, transfers: List<TransferLog>) {
                        // do nothing
                    }
                }
            )
    }

    @Test
    fun `rollback should call archiveService rollback`() {
        val blockNumber = 100L

        every { archiveService.rollback(blockNumber) } just Runs

        statefulLogsIndexer.rollback(blockNumber)

        verify(exactly = 1) { archiveService.rollback(blockNumber) }
    }
}
