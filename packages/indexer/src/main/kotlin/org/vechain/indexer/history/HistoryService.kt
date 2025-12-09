package org.vechain.indexer.history

import org.apache.commons.codec.digest.DigestUtils
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

@Profile("history")
@Service
open class HistoryService(
    private val historyRepository: HistoryRepository,
    private val mongoTemplate: MongoTemplate,
    private val blacklistClient: NftBlacklistClient,
) {

    open fun processEvents(events: List<IndexedEvent>, block: Block): List<IndexedHistoryEvent> {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()
        val processedTxs = mutableSetOf<String>()

        events.forEach { event ->
            val eventName = EventUtils.determineEventType(event.params) ?: return@forEach

            if (event.params.getEventType() == "TransferBatch") {
                historyEvents.addAll(processBatchTransferEvents(event))
            } else {
                historyEvents.add(createIndexedHistoryEvent(event, eventName))
            }
            processedTxs.add(event.txId)
        }

        historyEvents.addAll(getMissingTransactions(block, processedTxs))

        return historyEvents
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(events: List<IndexedHistoryEvent>) {
        historyRepository.saveAll(events)
    }

    open fun processBlacklistEvents(events: List<IndexedEvent>) {
        // Should only contain blacklist and whitelist events
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
            }
        val update = Update().set(IndexedHistoryEvent::isBlacklisted.name, false)
        mongoTemplate.updateMulti(query, update, IndexedHistoryEvent::class.java)
    }

    private fun processBatchTransferEvents(event: IndexedEvent): List<IndexedHistoryEvent> {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()

        val tokenIds = event.params.getReturnValues()["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.params.getReturnValues()["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            val contractAddress =
                event.address ?: error("No contract address in event ${event.txId}")
            historyEvents.add(
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
        return historyEvents
    }

    private fun createIndexedHistoryEvent(
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

        return IndexedHistoryEvent(
            id = DigestUtils.sha1Hex(event.id),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            txId = event.txId,
            contractAddress = event.address,
            origin = event.origin,
            eventName = eventName,
            gasPayer = event.gasPayer,
            from = event.params.getAsString("from"),
            to = event.params.getAsString("to"),
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
            tokenAddress = event.params.getAsString("tokenAddress"),
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

    private fun getMissingTransactions(
        block: Block,
        processedTxs: Set<String>,
    ): List<IndexedHistoryEvent> =
        block.transactions
            .filter { it.id !in processedTxs }
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
