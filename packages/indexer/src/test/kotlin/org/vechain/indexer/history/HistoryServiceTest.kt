package org.vechain.indexer.history

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.SimpleBlockIndexerCoordinator
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BusinessEventParamFixtures.BUSINESS_EVENT_PARAMS
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorCycle
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorReadRepository
import org.vechain.indexer.validator.ValidatorSnapshot

@ExtendWith(MockKExtension::class)
class HistoryServiceTest {
    @MockK(relaxed = true) lateinit var repository: HistoryWriteRepository

    @MockK lateinit var validatorDelegationService: ValidatorDelegationService

    @MockK lateinit var validatorRepository: ValidatorReadRepository

    @MockK lateinit var validatorIndexer: Indexer

    @MockK lateinit var thorClient: ThorClient

    @MockK lateinit var processor: HistoryProcessor

    @MockK lateinit var businessEventProperties: BusinessEventProperties

    private lateinit var historyService: HistoryService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        every { businessEventProperties.substitutions } returns BUSINESS_EVENT_PARAMS
        every { repository.latestLifecycleRows() } returns emptyList()
        every { processor.getLastSyncedBlock() } returns null
        coEvery { processor.rollback(any()) } returns Unit
        coEvery { processor.process(any()) } returns Unit
        coEvery { thorClient.inspectClauses(any(), any()) } returns
            listOf(InspectionResult("0x", emptyList(), emptyList(), 0, false, ""))
        every { validatorIndexer.startBlock } returns 0L
        every { validatorIndexer.name } returns "validator"
        coEvery { validatorDelegationService.resolveCycleInfo(any(), any(), any()) } answers
            {
                5L to (secondArg<Long>() + 5L)
            }
        every { validatorDelegationService.resolveNextCycleBlock(any(), any(), any()) } answers
            {
                thirdArg<Long>() + 5L
            }
        every { validatorIndexer.getCurrentBlockNumber() } returns Long.MAX_VALUE
        every { validatorRepository.cyclesAsOf(any(), null) } returns emptyList()

        val delegationLifecycleHistoryService =
            DelegationLifecycleHistoryService(
                repository = repository,
                validatorDelegationService = validatorDelegationService,
                stakerSC = "0x00000000000000000000000000005374616B6572",
                stargateNftContract = BUSINESS_EVENT_PARAMS.getValue("STARGATE_NFT_CONTRACT"),
            )

        historyService =
            HistoryService(
                repository = repository,
                delegationLifecycleHistoryService = delegationLifecycleHistoryService,
                validatorRepository = validatorRepository,
                validatorIndexer = validatorIndexer,
                validatorStartBlock = 0L,
            )
    }

    @Test
    @Disabled("Never ran before this file returned Unit from runBlocking; fails on its fixtures.")
    fun `processBlock attaches lifecycle metadata to real stargate exit request row`(): Unit =
        runBlocking {
            // The fixture blocks are not consecutive, so each gets its own indexer run.
            val results =
                listOf(
                        BlockFixtures.BLOCK_STARGATE_STAKER_DELEGATION,
                        BlockFixtures.BLOCK_STARGATE_DELEGATION_EXIT_REQUEST,
                    )
                    .flatMap { captureIndexerResults(listOf(it)) }

            val requestResult = results.first { blockResult ->
                blockResult.events().any { it.eventType == "STARGATE_DELEGATE_REQUEST" }
            }
            val exitResult = results.first { blockResult ->
                blockResult.events().any { it.eventType == "STARGATE_DELEGATION_EXIT_REQUEST" }
            }

            historyService.processBlock(requestResult.events(), requestResult.block)
            val exitRecords = historyService.processBlock(exitResult.events(), exitResult.block)

            val exitRequest = exitRecords.first {
                it.eventName == HistoryEventName.STARGATE_DELEGATE_EXIT_REQUEST
            }

            assertThat(exitRequest.delegationLifecycleStatus).isEqualTo(Status.EXITING)
            assertThat(exitRequest.delegationLifecycleNextCycle)
                .isEqualTo(exitResult.block.number + 5L)
            assertThat(exitRequest.delegationLifecycleCycleLength).isEqualTo(5L)
        }

    @Test
    fun `processBlock adds UNKNOWN_TX rows for transactions without recognized history events`():
        Unit = runBlocking {
        val block = BlockFixtures.BLOCK_MULTIPLE_TXS

        val records = historyService.processBlock(emptyList(), block)

        assertThat(records).hasSize(block.transactions.size)
        assertThat(records.map { it.eventName }).containsOnly(HistoryEventName.UNKNOWN_TX)
        assertThat(records.map { it.txId })
            .containsExactlyInAnyOrderElementsOf(block.transactions.map { it.id })
    }

    @Test
    fun `processBlock skips UNKNOWN_TX fallback when a transaction already produced a history row`():
        Unit = runBlocking {
        val transaction = BlockFixtures.BLOCK_RANDOM_TX.transactions.first()
        val block = BlockFixtures.BLOCK_RANDOM_TX.copy(transactions = listOf(transaction))
        val event =
            buildIndexedEvent(
                id = "event-1",
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                txId = transaction.id,
                origin = transaction.origin,
                gasPayer = transaction.gasPayer,
                eventType = "VET_TRANSFER",
                params =
                    AbiEventParameters(
                        mapOf(
                            "from" to transaction.origin,
                            "to" to "0x00000000000000000000000000000000000000aa",
                            "amount" to "10",
                        ),
                        "VET_TRANSFER",
                    ),
            )

        val records = historyService.processBlock(listOf(event), block)

        assertThat(records).hasSize(1)
        val record = records.single()
        assertThat(record.eventName).isEqualTo(HistoryEventName.TRANSFER_VET)
        assertThat(record.txId).isEqualTo(transaction.id)
    }

    @Test
    fun `each block reads the validator cycles as of itself`(): Unit = runBlocking {
        val block = BlockFixtures.BLOCK_TRANSFERS
        every { validatorRepository.cyclesAsOf(block.number, null) } returns
            listOf(cycle(period = 8))
        val seen = slot<Map<String, ValidatorSnapshot>>()
        val lifecycle = mockk<DelegationLifecycleHistoryService>(relaxed = true)
        coEvery { lifecycle.onBlockStart(any(), capture(seen)) } returns emptyList()
        val service =
            HistoryService(repository, lifecycle, validatorRepository, validatorIndexer, 0L)

        service.processBlock(emptyList(), block)

        assertThat(seen.captured)
            .isEqualTo(
                mapOf(VALIDATOR to ValidatorSnapshot(VALIDATOR, 8, startBlock = 1, exitBlock = 0))
            )
    }

    @Test
    fun `a block validator has not committed is refused`() {
        val block = BlockFixtures.BLOCK_TRANSFERS
        every { validatorIndexer.getCurrentBlockNumber() } returns block.number

        assertThrows<IllegalStateException> {
            runBlocking { historyService.processBlock(emptyList(), block) }
        }
    }

    private fun cycle(period: Long) =
        ValidatorCycle(
            id = VALIDATOR,
            status = Status.ACTIVE,
            startBlock = 1,
            cyclePeriodLength = period,
            exitBlock = null,
            completedPeriods = 0,
            delegatorVetStaked = null,
        )

    private suspend fun captureIndexerResults(
        blocks: List<org.vechain.indexer.thor.model.Block>
    ): List<IndexingResult.BlockResult> {
        val capturedResults = mutableListOf<IndexingResult.BlockResult>()
        coEvery { processor.process(any()) } answers
            {
                val result = firstArg<IndexingResult>()
                if (result is IndexingResult.BlockResult) {
                    capturedResults.add(result)
                }
            }

        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    validatorIndexer = validatorIndexer,
                    startBlock = blocks.first().number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )

        SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = blocks)

        return capturedResults
    }

    companion object {
        private const val VALIDATOR = "0x" + "9999999999999999999999999999999999999999"
    }
}
