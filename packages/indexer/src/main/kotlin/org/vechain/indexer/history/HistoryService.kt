package org.vechain.indexer.history

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.ProofUtils
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.nft.NftBlacklistClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsLong
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorRepository
import org.vechain.indexer.validator.ValidatorSnapshot

@Profile("history")
@Service
open class HistoryService(
    private val historyRepository: HistoryRepository,
    private val mongoTemplate: MongoTemplate,
    private val blacklistClient: NftBlacklistClient,
    private val delegationLifecycleHistoryService: DelegationLifecycleHistoryService,
    private val validatorRepository: ValidatorRepository,
    @param:Value("\${indexer.start-block.validator}") private val validatorStartBlock: Long,
) {
    /**
     * Normalizes a block into history rows by combining recognised event-derived history,
     * delegation lifecycle rows, and fallback `UNKNOWN_TX` rows for transactions that did not emit
     * a recognised history event.
     *
     * Delegation lifecycle processing only runs once the validator indexer's start block is reached
     * — before that, the staker built-in is not online so there is no validator state to read and
     * no delegations can exist.
     */
    open suspend fun processBlock(
        events: List<IndexedEvent>,
        block: Block,
    ): List<IndexedHistoryEvent> {
        val indexedHistoryEvents = mutableListOf<IndexedHistoryEvent>()
        val transactionIdsWithHistoryEvents = mutableSetOf<String>()
        val processDelegationLifecycle = block.number >= validatorStartBlock
        val validatorSnapshots: Map<String, ValidatorSnapshot> =
            if (processDelegationLifecycle) loadValidatorSnapshots() else emptyMap()

        if (processDelegationLifecycle) {
            indexedHistoryEvents.addAll(
                delegationLifecycleHistoryService.onBlockStart(block, validatorSnapshots)
            )
        }

        for ((index, event) in events.withIndex()) {
            val eventName = EventUtils.determineEventType(event.params)

            if (
                event.params.getEventType() == "TransferBatch" &&
                    eventName == HistoryEventName.TRANSFER_SF
            ) {
                // A TransferBatch event fans out into one history row per token id/value pair.
                indexedHistoryEvents.addAll(buildBatchTransferHistoryEvents(event))
                transactionIdsWithHistoryEvents.add(event.txId)
                continue
            }

            val historyEvent = eventName?.let { buildHistoryEvent(event, it) }

            if (processDelegationLifecycle) {
                val lifecycleResult =
                    delegationLifecycleHistoryService.onEvent(
                        event = event,
                        historyEvent = historyEvent,
                        block = block,
                        validatorSnapshots = validatorSnapshots,
                        order = 1_000 + index,
                    )

                lifecycleResult.historyEvent?.let {
                    indexedHistoryEvents.add(it)
                    transactionIdsWithHistoryEvents.add(event.txId)
                }
                indexedHistoryEvents.addAll(lifecycleResult.additionalEvents)
                lifecycleResult.additionalEvents.forEach {
                    transactionIdsWithHistoryEvents.add(it.txId)
                }
            } else {
                historyEvent?.let {
                    indexedHistoryEvents.add(it)
                    transactionIdsWithHistoryEvents.add(event.txId)
                }
            }
        }

        if (processDelegationLifecycle) {
            indexedHistoryEvents.addAll(
                delegationLifecycleHistoryService.onBlockEnd(block, validatorSnapshots)
            )
        }
        // Keep account history complete even when a transaction has no mapped history event.
        indexedHistoryEvents.addAll(
            buildUnknownTransactionHistoryEvents(block, transactionIdsWithHistoryEvents)
        )

        return indexedHistoryEvents
    }

    private fun loadValidatorSnapshots(): Map<String, ValidatorSnapshot> =
        validatorRepository.findByStatusNot(Status.WITHDRAWN).associate { v ->
            v.id to
                ValidatorSnapshot(
                    validatorId = v.id,
                    stakingPeriodLength = v.cyclePeriodLength ?: 0L,
                    startBlock = v.startBlock ?: 0L,
                    exitBlock = v.exitBlock ?: 0L,
                )
        }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(events: List<IndexedHistoryEvent>) {
        historyRepository.saveAll(events)
    }

    open fun processBlacklistEvents(events: List<IndexedEvent>) {
        // HistoryProcessor routes only blacklist-related events here; fail fast if that contract is
        // broken.
        assertEventTypes(events, "NFT_Blacklisted", "NFT_Whitelisted")

        val (blacklistAddresses, whitelistAddresses) = EventUtils.partitionBlacklistEvents(events)

        if (blacklistAddresses.isNotEmpty()) blacklist(blacklistAddresses)
        if (whitelistAddresses.isNotEmpty()) whitelist(whitelistAddresses)
    }

    /** Sets isBlacklisted to true for all history events related to the given contract addresses */
    protected fun blacklist(contractAddresses: List<String>) {
        if (contractAddresses.isEmpty()) return

        val query =
            Query().apply {
                addCriteria(
                    Criteria.where(IndexedHistoryEvent::contractAddress.name)
                        .`in`(contractAddresses)
                )
                // Required to engage the partial-filtered indexes on history_events.
                addCriteria(Criteria.where(IndexedHistoryEvent::blockNumber.name).exists(true))
            }
        val update = Update().set(IndexedHistoryEvent::isBlacklisted.name, true)
        mongoTemplate.updateMulti(query, update, IndexedHistoryEvent::class.java)
    }

    protected fun whitelist(contractAddresses: List<String>) {
        if (contractAddresses.isEmpty()) return

        val query =
            Query().apply {
                addCriteria(
                    Criteria.where(IndexedHistoryEvent::contractAddress.name)
                        .`in`(contractAddresses)
                )
                // Required to engage the partial-filtered indexes on history_events.
                addCriteria(Criteria.where(IndexedHistoryEvent::blockNumber.name).exists(true))
            }
        val update = Update().set(IndexedHistoryEvent::isBlacklisted.name, false)
        mongoTemplate.updateMulti(query, update, IndexedHistoryEvent::class.java)
    }

    open fun invalidateDelegationLifecycleState() {
        delegationLifecycleHistoryService.invalidate()
    }

    private suspend fun buildBatchTransferHistoryEvents(
        event: IndexedEvent
    ): List<IndexedHistoryEvent> {
        val indexedHistoryEvents = mutableListOf<IndexedHistoryEvent>()

        val tokenIds = event.params.getReturnValues()["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.params.getReturnValues()["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            val contractAddress =
                event.address ?: error("No contract address in event ${event.txId}")
            indexedHistoryEvents.add(
                IndexedHistoryEvent(
                    id = DigestUtils.sha1Hex("${event.id}-$i"),
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    txId = event.txId,
                    contractAddress = contractAddress,
                    origin = event.origin,
                    eventName = HistoryEventName.TRANSFER_SF,
                    gasPayer = event.gasPayer,
                    from = event.params.getAsString("from"),
                    to = event.params.getAsString("to"),
                    value = values.getOrNull(i)?.toString(),
                    tokenId = tokenIds.getOrNull(i)?.toString(),
                    isBlacklisted =
                        blacklistClient.isBlacklisted(
                            contractAddress,
                            BlockDetails(event.blockId, event.blockNumber, event.blockTimestamp),
                        ),
                )
            )
        }
        return indexedHistoryEvents
    }

    /** Maps one recognised indexed event into the normalised history document shape. */
    private suspend fun buildHistoryEvent(
        event: IndexedEvent,
        eventName: HistoryEventName,
    ): IndexedHistoryEvent {
        val tokenId =
            when (eventName) {
                HistoryEventName.TRANSFER_SF -> event.params.getAsString("id")
                else -> event.params.getAsString("tokenId")
            }

        val value =
            when (eventName) {
                HistoryEventName.TRANSFER_VET -> event.params.getAsString("amount")!!
                HistoryEventName.STARGATE_DELEGATE_REQUEST ->
                    event.params.getAsString("vetAmountStaked") ?: event.params.getAsString("value")
                HistoryEventName.B3TR_NAVIGATOR_DELEGATION_CREATED ->
                    event.params.getAsString("amount")
                HistoryEventName.B3TR_NAVIGATOR_DELEGATION_INCREASED ->
                    event.params.getAsString("addedAmount")
                HistoryEventName.B3TR_NAVIGATOR_DELEGATION_DECREASED ->
                    event.params.getAsString("removedAmount")
                HistoryEventName.B3TR_NAVIGATOR_DELEGATION_REMOVED ->
                    event.params.getAsString("amount")
                HistoryEventName.B3TR_NAVIGATOR_SLASHED,
                HistoryEventName.B3TR_NAVIGATOR_MINOR_SLASHED -> event.params.getAsString("amount")
                HistoryEventName.B3TR_NAVIGATOR_REGISTERED ->
                    event.params.getAsString("stakeAmount")
                HistoryEventName.B3TR_NAVIGATOR_STAKE_ADDED -> event.params.getAsString("amount")
                HistoryEventName.B3TR_NAVIGATOR_STAKE_WITHDRAWN ->
                    event.params.getAsString("amount")
                HistoryEventName.B3TR_NAVIGATOR_FEE_CLAIMED,
                HistoryEventName.B3TR_NAVIGATOR_FEE_DEPOSITED -> event.params.getAsString("amount")
                else -> event.params.getAsString("value")
            }

        val isBlacklisted =
            when (eventName) {
                HistoryEventName.TRANSFER_NFT,
                HistoryEventName.TRANSFER_SF -> {
                    val contractAddress =
                        event.address ?: error("No contract address in event ${event.txId}")
                    blacklistClient.isBlacklisted(
                        contractAddress,
                        BlockDetails(event.blockId, event.blockNumber, event.blockTimestamp),
                    )
                }
                else -> null
            }

        val isNavigatorDelegation =
            eventName in
                listOf(
                    HistoryEventName.B3TR_NAVIGATOR_DELEGATION_CREATED,
                    HistoryEventName.B3TR_NAVIGATOR_DELEGATION_INCREASED,
                    HistoryEventName.B3TR_NAVIGATOR_DELEGATION_DECREASED,
                    HistoryEventName.B3TR_NAVIGATOR_DELEGATION_REMOVED,
                )

        val isNavigatorEvent =
            eventName in
                listOf(
                    HistoryEventName.B3TR_NAVIGATOR_SLASHED,
                    HistoryEventName.B3TR_NAVIGATOR_MINOR_SLASHED,
                    HistoryEventName.B3TR_NAVIGATOR_REGISTERED,
                    HistoryEventName.B3TR_NAVIGATOR_STAKE_ADDED,
                    HistoryEventName.B3TR_NAVIGATOR_STAKE_WITHDRAWN,
                    HistoryEventName.B3TR_NAVIGATOR_FEE_CLAIMED,
                    HistoryEventName.B3TR_NAVIGATOR_FEE_DEPOSITED,
                )

        val from =
            when {
                isNavigatorDelegation -> event.params.getAsString("citizen")
                isNavigatorEvent -> event.params.getAsString("navigator")
                else -> event.params.getAsString("from")
            }

        val to =
            when {
                isNavigatorDelegation -> null
                isNavigatorEvent -> null
                else -> event.params.getAsString("to")
            }

        return IndexedHistoryEvent(
            id = DigestUtils.sha1Hex(event.id),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            txId = event.txId,
            contractAddress =
                when (eventName) {
                    HistoryEventName.NFT_SALE ->
                        event.params.getAsString("tokenAddress") ?: event.address
                    else -> event.address
                },
            origin = event.origin,
            eventName = eventName,
            gasPayer = event.gasPayer,
            from = from,
            to = to,
            value = value,
            tokenId = tokenId,
            appId = event.params.getAsString("appId"),
            proof = event.params.getAsString("proof")?.let { ProofUtils.parseProofFromJson(it) },
            roundId = event.params.getAsString("roundId"),
            proposalId = event.params.getAsString("proposalId"),
            appVotes =
                IndexedHistoryEvent.Companion.getAppVotes(
                    event.params.getReturnValues()["appsIds"],
                    event.params.getReturnValues()["voteWeights"],
                ),
            support = event.params.getAsInt("support")?.let { Support.Companion.fromValue(it) },
            voteWeight = event.params.getAsString("voteWeight"),
            votePower = event.params.getAsString("votePower"),
            reason = event.params.getAsString("reason"),
            oldLevel = event.params.getAsString("oldLevel"),
            newLevel = event.params.getAsString("newLevel"),
            inputToken = event.params.getAsString("inputToken"),
            outputToken = event.params.getAsString("outputToken"),
            inputValue = event.params.getAsString("inputValue"),
            outputValue = event.params.getAsString("outputValue"),
            owner = event.params.getAsString("owner"),
            delegationRewards = event.params.getAsString("delegationRewards"),
            vetGeneratedVthoRewards = event.params.getAsString("vetGeneratedVthoRewards"),
            migrated = event.params.getAsBoolean("migrated"),
            autorenew = event.params.getAsBoolean("autorenew"),
            levelId = event.params.getAsString("levelId"),
            tokenIds = event.params.getReturnValues()["tokenIds"] as? List<String>,
            validator = event.params.getAsString("validator"),
            delegationId = event.params.getAsString("delegationId"),
            periodClaimed = event.params.getAsLong("periodClaimed"),
            boostedBlocks = event.params.getAsString("boostedBlocks"),
            isBlacklisted = isBlacklisted,
        )
    }

    /** Fallback function to handle all unknown transaction types */
    private fun buildUnknownTransactionHistoryEvents(
        block: Block,
        transactionIdsWithHistoryEvents: Set<String>,
    ): List<IndexedHistoryEvent> =
        block.transactions
            .filter { it.id !in transactionIdsWithHistoryEvents }
            .map { tx ->
                IndexedHistoryEvent(
                    id = DigestUtils.sha1Hex(tx.id),
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    reverted = if (tx.reverted) true else null,
                    txId = tx.id,
                    origin = tx.origin,
                    eventName = HistoryEventName.UNKNOWN_TX,
                    gasPayer = tx.gasPayer,
                )
            }
}
