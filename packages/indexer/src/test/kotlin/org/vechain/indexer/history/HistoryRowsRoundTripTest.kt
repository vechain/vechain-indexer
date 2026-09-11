package org.vechain.indexer.history

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlin.reflect.full.memberProperties
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.SimpleBlockIndexerCoordinator
import org.vechain.indexer.config.BusinessEventProperties
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.fixtures.BusinessEventParamFixtures.BUSINESS_EVENT_PARAMS
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService

/** `assemble(flatten(e)) == e` for every row the indexer projects from every block fixture. */
class HistoryRowsRoundTripTest {

    private val repository = mockk<HistoryWriteRepository>(relaxed = true)
    private val thorClient = mockk<ThorClient>()
    private val validatorIndexer = mockk<Indexer>()
    private val validatorDelegationService = mockk<ValidatorDelegationService>()
    private val businessEventProperties = mockk<BusinessEventProperties>()
    private lateinit var historyService: HistoryService

    init {
        every { businessEventProperties.substitutions } returns BUSINESS_EVENT_PARAMS
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
        every { validatorDelegationService.nextStatus(any()) } answers
            {
                when (firstArg<Status>()) {
                    Status.QUEUED -> Status.ACTIVE
                    Status.EXITING -> Status.EXITED
                    else -> firstArg()
                }
            }
        coEvery { validatorDelegationService.getValidatorExitBlock(any(), any()) } returns 0L
        historyService =
            HistoryService(
                repository = repository,
                delegationLifecycleHistoryService =
                    DelegationLifecycleHistoryService(
                        repository = repository,
                        validatorDelegationService = validatorDelegationService,
                        stakerSC = "0x00000000000000000000000000005374616B6572",
                        stargateNftContract =
                            BUSINESS_EVENT_PARAMS.getValue("STARGATE_NFT_CONTRACT"),
                    ),
                validatorRepository = mockk { every { findAll() } returns emptyList() },
                validatorStartBlock = 0L,
            )
    }

    private fun fixtures(): List<Pair<String, Block>> =
        BlockFixtures::class
            .memberProperties
            .filter { it.returnType.classifier == Block::class }
            .map { it.name to it.get(BlockFixtures) as Block }

    /** Runs the real history indexer over one block so the events are decoded as in production. */
    private fun decode(block: Block): IndexingResult.BlockResult {
        val captured = mutableListOf<IndexingResult.BlockResult>()
        val processor = mockk<HistoryProcessor>()
        every { processor.getLastSyncedBlock() } returns null
        coEvery { processor.rollback(any()) } returns Unit
        coEvery { processor.process(any()) } answers
            {
                (firstArg<IndexingResult>() as? IndexingResult.BlockResult)?.let(captured::add)
            }
        val indexer =
            HistoryConfig()
                .historyIndexer(
                    thorClient = thorClient,
                    processor = processor,
                    validatorIndexer = validatorIndexer,
                    startBlock = block.number,
                    syncLoggerInterval = 1L,
                    bEProperties = businessEventProperties,
                )
        runBlocking {
            SimpleBlockIndexerCoordinator.launch(indexer = indexer, blocks = listOf(block))
        }
        return captured.single()
    }

    @TestFactory
    fun `every fixture block survives flatten and assemble`(): List<DynamicTest> =
        fixtures().map { (name, block) ->
            dynamicTest(name) {
                val result = decode(block)
                val rows = runBlocking {
                    historyService.processBlock(result.events(), result.block)
                }
                assertTrue(
                    rows.isNotEmpty() || block.transactions.isEmpty(),
                    "$name projects no rows",
                )
                rows.forEach { event ->
                    val assembled =
                        HistoryRowMapping.assemble(HistoryRowMapping.flatten(event).event)
                    assertEquals(normalise(event), assembled, "${event.eventName} ${event.id}")
                }
            }
        }

    /** What the row cannot carry: the derived fan-out, and numbers hidden behind a String type. */
    private fun normalise(e: IndexedHistoryEvent) =
        e.copy(
            isBlacklisted = null,
            involvedAddresses = null,
            tokenIds = (e.tokenIds as List<*>?)?.map { it.toString() },
        )
}
