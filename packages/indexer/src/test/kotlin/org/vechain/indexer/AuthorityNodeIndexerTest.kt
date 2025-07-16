package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.model.AuthorityNodeEndorser
import org.vechain.indexer.repository.AuthorityNodeRepository
import org.vechain.indexer.service.AuthorityNodesService
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.model.EventLog

@ExtendWith(MockKExtension::class)
class AuthorityNodeIndexerTest {
    @MockK lateinit var authorityNodeRepository: AuthorityNodeRepository

    @MockK lateinit var authorityNodeService: AuthorityNodesService

    @MockK lateinit var thorService: ThorService

    private lateinit var abiManager: AbiManager
    private lateinit var indexer: AuthorityNodeIndexer

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        abiManager = mockk(relaxed = true)

        indexer =
            AuthorityNodeIndexer(
                authorityNodeRepository = authorityNodeRepository,
                authorityNodeService = authorityNodeService,
                thorService = thorService,
                thorClient = DefaultThorClient("http://localhost:8669"),
                abiManager = abiManager,
                syncLogInterval = 1000L,
                syncBlockBatchSize = 1000L,
                contractAddress = "0xabc",
            )
    }

    @Test
    fun `getLastSyncedBlock - returns best block if DB is empty`() {
        every { authorityNodeRepository.count() } returns 0L
        every { thorService.getBestBlock() } returns BlockFixtures.BLOCK_MP_SALES

        val result = indexer.getLastSyncedBlock()

        assert(result!!.number == BlockFixtures.BLOCK_MP_SALES.number)
        verify { thorService.getBestBlock() }
    }

    @Test
    fun `getLastSyncedBlock - delegates to super if DB is not empty`() {
        every { authorityNodeRepository.count() } returns 5L
        val superResult = AuthorityNodeEndorser("0xabc", 50, blockTimestamp = 123L, blockId = "a")
        every { authorityNodeRepository.getLatestRecord() } returns superResult

        val result = indexer.getLastSyncedBlock()

        assert(result!!.number == superResult.blockNumber)
    }

    @Test
    fun `processLogs - calls sync and process when DB is empty`() {
        every { authorityNodeRepository.count() } returns 0L
        every { authorityNodeService.syncEndorsersForAllNodes() } just Runs
        every { authorityNodeService.processCandidateEvents(any()) } just Runs

        val logs = listOf<EventLog>() // could be mock data
        indexer.processLogs(logs, emptyList())

        verify { authorityNodeService.syncEndorsersForAllNodes() }
        verify { authorityNodeService.processCandidateEvents(any()) }
    }

    @Test
    fun `processLogs - skips sync when already synced`() {
        every { authorityNodeRepository.count() } returnsMany listOf(0L, 1L)
        every { authorityNodeService.syncEndorsersForAllNodes() } just Runs
        every { authorityNodeService.processCandidateEvents(any()) } just Runs

        indexer.processLogs(emptyList(), emptyList()) // should sync
        indexer.processLogs(emptyList(), emptyList()) // should not sync again

        verify(exactly = 1) { authorityNodeService.syncEndorsersForAllNodes() }
    }

    @Test
    fun `rollback - deletes blocks in range`() {
        every { authorityNodeRepository.deleteAllByBlockNumberBetween(9, 11) } just Runs

        indexer.rollback(10)

        verify { authorityNodeRepository.deleteAllByBlockNumberBetween(9, 11) }
    }
}
