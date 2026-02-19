package org.vechain.indexer.amn

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded

@ExtendWith(MockKExtension::class)
class AmnProcessorTest {
    @MockK lateinit var amnRepository: AmnRepository

    @MockK lateinit var amnService: AmnService

    @MockK lateinit var thorClient: ThorClient

    @MockK lateinit var checkpointService: CheckpointService

    private val processorMetrics: ProcessorMetrics = mockk(relaxed = true)

    private lateinit var processor: AmnProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        processor =
            AmnProcessor(
                repository = amnRepository,
                amnService = amnService,
                thorClient = thorClient,
                checkpointService = checkpointService,
                processorMetrics = processorMetrics,
            )
    }

    @Test
    fun `getLastSyncedBlock - returns finalized block if DB is empty`() {
        every { amnRepository.count() } returns 0L
        coEvery { thorClient.getBlockUnexpanded(BlockRevision.Keyword.FINALIZED) } returns
            asUnexpanded(BlockFixtures.BLOCK_MP_SALES)

        val result = processor.getLastSyncedBlock()

        assert(result!!.number == BlockFixtures.BLOCK_MP_SALES.number)
        coVerify { thorClient.getBlockUnexpanded(BlockRevision.Keyword.FINALIZED) }
    }

    @Test
    fun `getLastSyncedBlock - delegates to super if DB is not empty`() {
        every { amnRepository.count() } returns 5L
        val superResult = AmnEndorser("0xabc", 50, blockTimestamp = 123L, blockId = "a")
        every { amnRepository.getLatestRecord() } returns superResult
        every { checkpointService.getCheckpoint(any()) } returns null

        val result = processor.getLastSyncedBlock()

        assert(result!!.number == superResult.blockNumber)
    }

    @Test
    fun `process - calls sync and process when DB is empty`() {
        every { amnRepository.count() } returns 0L
        coEvery { amnService.syncEndorsersForAllNodes() } returns Unit
        coEvery { amnService.processCandidateEvents(any()) } returns emptyList()

        runBlocking {
            processor.process(IndexingResult.EventsOnly(100, emptyList(), Status.SYNCING))
        }

        coVerify { amnService.syncEndorsersForAllNodes() }
        coVerify { amnService.processCandidateEvents(any()) }
    }

    @Test
    fun `process - skips sync when already synced`() {
        every { amnRepository.count() } returnsMany listOf(0L, 1L)
        coEvery { amnService.syncEndorsersForAllNodes() } returns Unit
        coEvery { amnService.processCandidateEvents(any()) } returns emptyList()

        runBlocking {
            processor.process(IndexingResult.EventsOnly(100, emptyList(), Status.SYNCING))
        }
        runBlocking {
            processor.process(IndexingResult.EventsOnly(100, emptyList(), Status.SYNCING))
        }

        coVerify(exactly = 1) { amnService.syncEndorsersForAllNodes() }
    }

    private fun asUnexpanded(block: org.vechain.indexer.thor.model.Block): BlockUnexpanded =
        BlockUnexpanded(
            number = block.number,
            id = block.id,
            size = block.size,
            parentID = block.parentID,
            timestamp = block.timestamp,
            gasLimit = block.gasLimit,
            baseFeePerGas = block.baseFeePerGas,
            beneficiary = block.beneficiary,
            gasUsed = block.gasUsed,
            totalScore = block.totalScore,
            txsRoot = block.txsRoot,
            txsFeatures = block.txsFeatures,
            stateRoot = block.stateRoot,
            receiptsRoot = block.receiptsRoot,
            com = block.com,
            signer = block.signer,
            isTrunk = block.isTrunk,
            isFinalized = block.isFinalized,
            transactions = emptyList(),
        )
}
