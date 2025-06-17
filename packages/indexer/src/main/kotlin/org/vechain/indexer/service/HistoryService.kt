package org.vechain.indexer.service

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedHistoryEvent
import org.vechain.indexer.model.b3tr.ProposalSupport
import org.vechain.indexer.model.history.HistoryEventName
import org.vechain.indexer.repository.HistoryEventRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.EventUtils.determineEventType
import org.vechain.indexer.utils.ParamUtils.getAsBoolean
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("history-events")
@Service
class HistoryService(
    private val historyRepository: HistoryEventRepository,
    private val mongoTemplate: MongoTemplate,
    @Value("\${contracts.stargate_delegation}")
    private val stargateDelegationContractAddress: String,
) {
    fun processBlockEvents(events: List<IndexedEvent>, block: Block) {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()
        val processedTxs = mutableSetOf<String>()

        events.forEach { event ->
            val eventName = determineEventType(event.params) ?: return@forEach

            if (event.params.getEventType() == "TransferBatch") {
                historyEvents.addAll(processBatchTransferEvents(event))
            } else {
                historyEvents.add(createIndexedHistoryEvent(event, eventName))
            }
            processedTxs.add(event.txId)
        }

        historyEvents.addAll(getMissingTransactions(block, processedTxs))

        mongoTemplate.insert(historyEvents, IndexedHistoryEvent::class.java)
    }

    private fun processBatchTransferEvents(event: IndexedEvent): List<IndexedHistoryEvent> {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()

        val tokenIds = event.params.getReturnValues()["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.params.getReturnValues()["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            historyEvents.add(
                IndexedHistoryEvent(
                    id = DigestUtils.sha1Hex("${event.id}-$i"),
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    txId = event.txId,
                    contractAddress = event.address,
                    origin = event.origin,
                    eventName = HistoryEventName.TRANSFER_SF,
                    gasPayer = event.gasPayer,
                    from = event.params.getAsString("from"),
                    to = event.params.getAsString("to"),
                    value = values.getOrNull(i)?.toString(),
                    tokenId = tokenIds.getOrNull(i)?.toString(),
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
                HistoryEventName.STARGATE_DELEGATE -> event.params.getAsString("vetAmountStaked")!!
                HistoryEventName.STARGATE_CLAIM_REWARDS ->
                    EventUtils.getStargateRewards(event.params)
                else -> event.params.getAsString("value")
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
            proof = event.params.getAsString("proof"),
            roundId = event.params.getAsString("roundId"),
            proposalId = event.params.getAsString("proposalId"),
            appVotes =
                IndexedHistoryEvent.getAppVotes(
                    event.params.getReturnValues()["appsIds"],
                    event.params.getReturnValues()["voteWeights"],
                ),
            support = event.params.getAsInt("support")?.let { ProposalSupport.fromValue(it) },
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

    fun rollback(blockNumber: Long) {
        historyRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
