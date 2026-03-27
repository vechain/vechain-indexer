package org.vechain.indexer.history

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.bson.Document
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationResults
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorDelegationService
import org.vechain.indexer.validator.ValidatorSnapshot

class DelegationLifecycleHistoryServiceTest {
    private val mongoTemplate = mockk<MongoTemplate>()
    private val validatorDelegationService = mockk<ValidatorDelegationService>()

    private lateinit var service: DelegationLifecycleHistoryService

    @BeforeEach
    fun setup() {
        clearAllMocks()
        every { mongoTemplate.getCollectionName(IndexedHistoryEvent::class.java) } returns "history"
        every {
            mongoTemplate.aggregate(any<Aggregation>(), "history", IndexedHistoryEvent::class.java)
        } returns AggregationResults(emptyList(), Document())

        service =
            DelegationLifecycleHistoryService(
                mongoTemplate = mongoTemplate,
                validatorDelegationService = validatorDelegationService,
                stakerSC = "0xSTAKER",
                stargateNftContract = "0xNFT",
            )
    }

    @Test
    fun `delegate request emits active history event at next cycle`() = runBlocking {
        coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
            (5L to 10L)
        every { validatorDelegationService.nextStatus(Status.QUEUED) } returns Status.ACTIVE

        val request =
            event(
                type = HistoryEventName.STARGATE_DELEGATE_REQUEST.name,
                params =
                    mapOf(
                        "delegationId" to "d1",
                        "tokenId" to "t1",
                        "validator" to "0xVAL",
                        "owner" to "0xOWNER",
                    ),
                txId = "tx-request",
            )

        val result =
            service.onEvent(
                event = request,
                historyEvent =
                    historyRow(
                        eventName = HistoryEventName.STARGATE_DELEGATE_REQUEST,
                        delegationId = "d1",
                        tokenId = "t1",
                        validator = "0xVAL",
                        owner = "0xOWNER",
                        txId = "tx-request",
                        block = block(5),
                    ),
                block = block(5),
                validatorSnapshots = emptyMap(),
                order = 1000,
            )

        assertThat(result.historyEvent?.delegationLifecycleStatus).isEqualTo(Status.QUEUED)
        assertThat(result.historyEvent?.delegationLifecycleNextCycle).isEqualTo(10L)

        val synthetic = service.onBlockStart(block(10), emptyMap())

        assertThat(synthetic).hasSize(1)
        assertThat(synthetic.first().eventName).isEqualTo(HistoryEventName.STARGATE_DELEGATE_ACTIVE)
        assertThat(synthetic.first().owner).isEqualTo("0xOWNER")
        assertThat(synthetic.first().txId).isEqualTo("tx-request")
    }

    @Test
    fun `transfer before activation updates synthetic owner`() = runBlocking {
        coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
            (5L to 10L)
        every { validatorDelegationService.nextStatus(Status.QUEUED) } returns Status.ACTIVE

        service.onEvent(
            event =
                event(
                    type = HistoryEventName.STARGATE_DELEGATE_REQUEST.name,
                    params =
                        mapOf(
                            "delegationId" to "d1",
                            "tokenId" to "t1",
                            "validator" to "0xVAL",
                            "owner" to "0xOWNER",
                        ),
                    txId = "tx-request",
                ),
            historyEvent =
                historyRow(
                    eventName = HistoryEventName.STARGATE_DELEGATE_REQUEST,
                    delegationId = "d1",
                    tokenId = "t1",
                    validator = "0xVAL",
                    owner = "0xOWNER",
                    txId = "tx-request",
                    block = block(5),
                ),
            block = block(5),
            validatorSnapshots = emptyMap(),
            order = 1000,
        )

        val transferResult =
            service.onEvent(
                event =
                    event(
                        type = "Transfer",
                        params = mapOf("tokenId" to "t1", "from" to "0xOWNER", "to" to "0xNEW"),
                        address = "0xNFT",
                        txId = "tx-transfer",
                    ),
                historyEvent =
                    historyRow(
                        eventName = HistoryEventName.TRANSFER_NFT,
                        tokenId = "t1",
                        txId = "tx-transfer",
                        block = block(6),
                    ),
                block = block(6),
                validatorSnapshots = emptyMap(),
                order = 1001,
            )

        assertThat(transferResult.historyEvent?.delegationLifecycleStatus).isEqualTo(Status.QUEUED)

        val synthetic = service.onBlockStart(block(10), emptyMap())

        assertThat(synthetic).hasSize(1)
        assertThat(synthetic.first().owner).isEqualTo("0xNEW")
        assertThat(synthetic.first().txId).isEqualTo("tx-transfer")
    }

    @Test
    fun `validator exit request produces exited validator history event on exit block`() =
        runBlocking {
            coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
                (5L to 30L)
            coEvery { validatorDelegationService.getValidatorExitBlock("0xVAL", any()) } returns 20L
            every { validatorDelegationService.nextStatus(Status.EXITING) } returns Status.EXITED

            service.onEvent(
                event =
                    event(
                        type = HistoryEventName.STARGATE_DELEGATE_REQUEST.name,
                        params =
                            mapOf(
                                "delegationId" to "d1",
                                "tokenId" to "t1",
                                "validator" to "0xVAL",
                                "owner" to "0xOWNER",
                            ),
                        txId = "tx-request",
                    ),
                historyEvent =
                    historyRow(
                        eventName = HistoryEventName.STARGATE_DELEGATE_REQUEST,
                        delegationId = "d1",
                        tokenId = "t1",
                        validator = "0xVAL",
                        owner = "0xOWNER",
                        txId = "tx-request",
                        block = block(5),
                    ),
                block = block(5),
                validatorSnapshots = emptyMap(),
                order = 1000,
            )

            service.onEvent(
                event =
                    event(
                        type = "ValidatorExitRequested",
                        params = mapOf("validator" to "0xVAL"),
                        address = "0xSTAKER",
                        txId = "tx-validator-exit",
                    ),
                historyEvent = null,
                block = block(7),
                validatorSnapshots = mapOf("0xVAL" to snapshot("0xVAL", 20L)),
                order = 1001,
            )

            val synthetic =
                service.onBlockStart(block(20), mapOf("0xVAL" to snapshot("0xVAL", 20L)))

            assertThat(synthetic).hasSize(1)
            assertThat(synthetic.first().eventName)
                .isEqualTo(HistoryEventName.STARGATE_DELEGATION_EXITED_VALIDATOR)
            assertThat(synthetic.first().txId).isEqualTo("tx-validator-exit")
        }

    private fun block(number: Long) =
        Block(
            id = "b$number",
            number = number,
            timestamp = number * 100,
            parentID = "p",
            size = 1,
            gasLimit = 1,
            baseFeePerGas = "0x",
            beneficiary = "b",
            gasUsed = 1,
            totalScore = 1,
            txsRoot = "r",
            txsFeatures = 0,
            stateRoot = "s",
            receiptsRoot = "r2",
            com = false,
            signer = "s",
            isTrunk = true,
            isFinalized = true,
            transactions = emptyList(),
        )

    private fun event(
        type: String,
        params: Map<String, Any>,
        address: String = "0xcontract",
        txId: String,
    ) =
        IndexedEvent(
            id = "$type-$txId",
            blockId = "b1",
            blockNumber = 1,
            blockTimestamp = 100,
            txId = txId,
            origin = "0xOWNER",
            paid = null,
            gasUsed = null,
            gasPayer = null,
            raw = null,
            params = AbiEventParameters(returnValues = params),
            address = address,
            eventType = type,
            clauseIndex = 0,
            signature = null,
        )

    private fun historyRow(
        eventName: HistoryEventName,
        block: Block,
        txId: String,
        delegationId: String? = null,
        tokenId: String? = null,
        validator: String? = null,
        owner: String? = null,
    ) =
        IndexedHistoryEvent(
            id = "$eventName-$txId",
            blockId = block.id,
            blockNumber = block.number,
            blockTimestamp = block.timestamp,
            txId = txId,
            eventName = eventName,
            delegationId = delegationId,
            tokenId = tokenId,
            validator = validator,
            owner = owner,
            origin = owner,
        )

    private fun snapshot(validatorId: String, exitBlock: Long) =
        ValidatorSnapshot(
            validatorId = validatorId,
            stakingPeriodLength = 5L,
            startBlock = 1L,
            exitBlock = exitBlock,
        )
}
