package org.vechain.indexer.service

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedHistoryEvent
import org.vechain.indexer.model.b3tr.ProposalSupport
import org.vechain.indexer.model.history.HistoryEventName
import org.vechain.indexer.repository.HistoryEventRepository
import org.vechain.indexer.thor.model.EventLog
import org.vechain.indexer.utils.EventUtils.determineEventType
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("history-events")
@Service
class HistoryService(
    private val historyRepository: HistoryEventRepository,
    private val mongoTemplate: MongoTemplate,
) {
    fun processBlockEvents(
        events: List<Pair<IndexedEvent, GenericEventParameters>>,
        allContractEvents: List<EventLog>,
    ) {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()
        val processedTxs = mutableSetOf<String>()

        events.forEach { event ->
            val eventName = determineEventType(event.second) ?: return@forEach

            if (event.second.getEventType() == "TransferBatch") {
                historyEvents.addAll(processBatchTransferEvents(event))
            } else {
                historyEvents.add(createIndexedHistoryEvent(event, eventName))
            }
            processedTxs.add(event.first.txId)
        }

        historyEvents.addAll(getMissingTransactions(allContractEvents, processedTxs))

        mongoTemplate.insert(historyEvents, IndexedHistoryEvent::class.java)
    }

    private fun processBatchTransferEvents(
        event: Pair<IndexedEvent, GenericEventParameters>
    ): List<IndexedHistoryEvent> {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()

        val tokenIds = event.second.params["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.second.params["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            historyEvents.add(
                IndexedHistoryEvent(
                    id = DigestUtils.sha1Hex("${event.first.id}-$i"),
                    blockId = event.first.blockId,
                    blockNumber = event.first.blockNumber,
                    blockTimestamp = event.first.blockTimestamp,
                    txId = event.first.txId,
                    contractAddress = event.first.address,
                    origin = event.first.origin,
                    eventName = HistoryEventName.TRANSFER_SF,
                    gasPayer = event.first.gasPayer,
                    from = event.second.params.getAsString("from"),
                    to = event.second.params.getAsString("to"),
                    value = values.getOrNull(i)?.toString(),
                    tokenId = tokenIds.getOrNull(i)?.toString(),
                ),
            )
        }
        return historyEvents
    }

    private fun createIndexedHistoryEvent(
        event: Pair<IndexedEvent, GenericEventParameters>,
        eventName: HistoryEventName,
    ): IndexedHistoryEvent {
        val tokenId =
            when (eventName) {
                HistoryEventName.TRANSFER_SF -> event.second.params.getAsString("id")
                else -> event.second.params.getAsString("tokenId")
            }

        val value =
            when (eventName) {
                HistoryEventName.TRANSFER_VET -> event.second.params.getAsString("amount")!!
                else -> event.second.params.getAsString("value")
            }

        return IndexedHistoryEvent(
            id = DigestUtils.sha1Hex(event.first.id),
            blockId = event.first.blockId,
            blockNumber = event.first.blockNumber,
            blockTimestamp = event.first.blockTimestamp,
            txId = event.first.txId,
            contractAddress = event.first.address,
            origin = event.first.origin,
            eventName = eventName,
            gasPayer = event.first.gasPayer,
            from = event.second.params.getAsString("from"),
            to = event.second.params.getAsString("to"),
            value = value,
            tokenId = tokenId,
            appId = event.second.params.getAsString("appId"),
            proof = event.second.params.getAsString("proof"),
            roundId = event.second.params.getAsString("roundId"),
            proposalId = event.second.params.getAsString("proposalId"),
            appVotes =
                IndexedHistoryEvent.getAppVotes(
                    event.second.params["appsIds"],
                    event.second.params["voteWeights"],
                ),
            support =
                event.second.params.getAsInt("support")?.let { ProposalSupport.fromValue(it) },
            voteWeight = event.second.params.getAsString("voteWeight"),
            votePower = event.second.params.getAsString("votePower"),
            reason = event.second.params.getAsString("reason"),
            oldLevel = event.second.params.getAsString("oldLevel"),
            newLevel = event.second.params.getAsString("newLevel"),
            inputToken = event.second.params.getAsString("inputToken"),
            outputToken = event.second.params.getAsString("outputToken"),
            inputValue = event.second.params.getAsString("inputValue"),
            outputValue = event.second.params.getAsString("outputValue"),
        )
    }

    private fun getMissingTransactions(
        allContractEvents: List<EventLog>,
        processedTxs: Set<String>,
    ): List<IndexedHistoryEvent> =
        allContractEvents
            .filter { it.meta.txID !in processedTxs } // Exclude already processed TXs
            .groupBy { it.meta.txID } // Group events by TX ID
            .map { (_, events) -> events.first() } // Pick the first event for each TX
            .map { tx ->
                IndexedHistoryEvent(
                    id = DigestUtils.sha1Hex(tx.meta.txID),
                    blockId = tx.meta.blockID,
                    blockNumber = tx.meta.blockNumber,
                    blockTimestamp = tx.meta.blockTimestamp,
                    txId = tx.meta.txID,
                    origin = tx.meta.txOrigin,
                    eventName = HistoryEventName.UNKNOWN_TX,
                )
            }

    fun rollback(blockNumber: Long) {
        println("Rolling back block $blockNumber")
        historyRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1000)
    }
}
