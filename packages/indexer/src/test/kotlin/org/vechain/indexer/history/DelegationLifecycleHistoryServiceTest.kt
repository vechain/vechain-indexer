package org.vechain.indexer.history

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
    fun `queued delegation activates at validator start block once start becomes known`() =
        runBlocking {
            coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
                (5L to 0L)
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
                validatorSnapshots = mapOf("0xVAL" to snapshot("0xVAL", startBlock = 0L)),
                order = 1000,
            )

            val blockEndSynthetic =
                service.onBlockEnd(block(9), mapOf("0xVAL" to snapshot("0xVAL", startBlock = 0L)))

            assertThat(blockEndSynthetic).isEmpty()

            val synthetic =
                service.onBlockStart(
                    block(10),
                    mapOf("0xVAL" to snapshot("0xVAL", startBlock = 10L)),
                )

            assertThat(synthetic).hasSize(1)
            assertThat(synthetic.first().eventName)
                .isEqualTo(HistoryEventName.STARGATE_DELEGATE_ACTIVE)
            assertThat(synthetic.first().blockNumber).isEqualTo(10L)
            coVerify(exactly = 1) {
                validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any())
            }
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
            assertThat(synthetic.first().txId).isEqualTo("tx-request")
        }

    @Test
    fun `raw stargate exit request updates lifecycle state and emits exited event next cycle`() =
        runBlocking {
            coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
                (5L to 10L)
            every { validatorDelegationService.nextStatus(Status.QUEUED) } returns Status.ACTIVE
            every { validatorDelegationService.nextStatus(Status.EXITING) } returns Status.EXITED
            every { validatorDelegationService.resolveNextCycleBlock(10L, 5L, 11L) } returns 15L

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

            val active = service.onBlockStart(block(10), emptyMap())

            assertThat(active).hasSize(1)
            assertThat(active.first().eventName)
                .isEqualTo(HistoryEventName.STARGATE_DELEGATE_ACTIVE)

            val exitRequest =
                service.onEvent(
                    event =
                        event(
                            type = "STARGATE_DELEGATION_EXIT_REQUEST",
                            params =
                                mapOf(
                                    "delegationId" to "d1",
                                    "tokenId" to "t1",
                                    "validator" to "0xVAL",
                                    "owner" to "0xOWNER",
                                ),
                            txId = "tx-exit-request",
                        ),
                    historyEvent =
                        historyRow(
                            eventName = HistoryEventName.STARGATE_DELEGATE_EXIT_REQUEST,
                            delegationId = "d1",
                            tokenId = "t1",
                            validator = "0xVAL",
                            owner = "0xOWNER",
                            txId = "tx-exit-request",
                            block = block(11),
                        ),
                    block = block(11),
                    validatorSnapshots = emptyMap(),
                    order = 1001,
                )

            assertThat(exitRequest.historyEvent?.delegationLifecycleStatus)
                .isEqualTo(Status.EXITING)
            assertThat(exitRequest.historyEvent?.delegationLifecycleNextCycle).isEqualTo(15L)

            val exited = service.onBlockStart(block(15), emptyMap())

            assertThat(exited).hasSize(1)
            assertThat(exited.first().eventName)
                .isEqualTo(HistoryEventName.STARGATE_DELEGATION_EXITED)
            assertThat(exited.first().txId).isEqualTo("tx-request")
        }

    @Test
    fun `user exit request overrides prior validator-forced exit classification`() = runBlocking {
        coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
            (5L to 10L)
        coEvery { validatorDelegationService.getValidatorExitBlock("0xVAL", any()) } returns 20L
        every { validatorDelegationService.nextStatus(Status.QUEUED) } returns Status.ACTIVE
        every { validatorDelegationService.nextStatus(Status.EXITING) } returns Status.EXITED
        every { validatorDelegationService.resolveNextCycleBlock(10L, 5L, 11L) } returns 15L

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

        service.onBlockStart(block(10), emptyMap())

        service.onEvent(
            event =
                event(
                    type = "ValidatorExitRequested",
                    params = mapOf("validator" to "0xVAL"),
                    address = "0xSTAKER",
                    txId = "tx-validator-exit",
                ),
            historyEvent = null,
            block = block(10),
            validatorSnapshots = mapOf("0xVAL" to snapshot("0xVAL", exitBlock = 20L)),
            order = 1001,
        )

        val exitRequest =
            service.onEvent(
                event =
                    event(
                        type = "STARGATE_DELEGATION_EXIT_REQUEST",
                        params =
                            mapOf(
                                "delegationId" to "d1",
                                "tokenId" to "t1",
                                "validator" to "0xVAL",
                                "owner" to "0xOWNER",
                            ),
                        txId = "tx-exit-request",
                    ),
                historyEvent =
                    historyRow(
                        eventName = HistoryEventName.STARGATE_DELEGATE_EXIT_REQUEST,
                        delegationId = "d1",
                        tokenId = "t1",
                        validator = "0xVAL",
                        owner = "0xOWNER",
                        txId = "tx-exit-request",
                        block = block(11),
                    ),
                block = block(11),
                validatorSnapshots = emptyMap(),
                order = 1002,
            )

        assertThat(exitRequest.historyEvent?.delegationLifecycleStatus).isEqualTo(Status.EXITING)
        assertThat(exitRequest.historyEvent?.delegationLifecycleForceExit).isFalse()

        val exited = service.onBlockStart(block(15), emptyMap())

        assertThat(exited).hasSize(1)
        assertThat(exited.first().eventName).isEqualTo(HistoryEventName.STARGATE_DELEGATION_EXITED)
        assertThat(exited.first().txId).isEqualTo("tx-request")
    }

    @Test
    fun `validator disappearance emits synthetic exit history row and prevents later activation`() =
        runBlocking {
            coEvery { validatorDelegationService.resolveCycleInfo("0xVAL", 5L, any()) } returns
                (5L to 15L)

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

            val synthetic =
                service.onBlockEnd(
                    block(11),
                    mapOf("0xOTHER" to snapshot("0xOTHER", startBlock = 1L)),
                )

            assertThat(synthetic).hasSize(1)
            assertThat(synthetic.first().eventName)
                .isEqualTo(HistoryEventName.STARGATE_DELEGATION_EXITED_VALIDATOR)
            assertThat(synthetic.first().txId).isEqualTo("tx-request")
            assertThat(synthetic.first().blockNumber).isEqualTo(11L)

            val later = service.onBlockStart(block(15), emptyMap())

            assertThat(later).isEmpty()
        }

    @Test
    fun `ensureLoaded only aggregates rows with lifecycle status present`() {
        val aggregationSlot = slot<Aggregation>()
        every {
            mongoTemplate.aggregate(
                capture(aggregationSlot),
                "history",
                IndexedHistoryEvent::class.java,
            )
        } returns AggregationResults(emptyList(), Document())

        service.onBlockEnd(block(1), emptyMap())

        val matchStage = aggregationSlot.captured.toPipeline(Aggregation.DEFAULT_CONTEXT).first()
        val lifecycleMatch =
            matchStage
                .get("\$match", Document::class.java)
                .get(IndexedHistoryEvent.DELEGATION_LIFECYCLE_STATUS_FIELD, Document::class.java)

        assertThat(lifecycleMatch).containsEntry("\$exists", true)
        assertThat(lifecycleMatch).containsKey("\$ne")
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

    private fun snapshot(
        validatorId: String,
        exitBlock: Long = 0L,
        startBlock: Long = 1L,
        stakingPeriodLength: Long = 5L,
    ) =
        ValidatorSnapshot(
            validatorId = validatorId,
            stakingPeriodLength = stakingPeriodLength,
            startBlock = startBlock,
            exitBlock = exitBlock,
        )
}
