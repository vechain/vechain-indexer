package org.vechain.indexer.service

import org.apache.commons.codec.digest.DigestUtils
import org.springframework.stereotype.Service
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedHistoryEvent
import org.vechain.indexer.model.b3tr.ProposalSupport
import org.vechain.indexer.model.history.HistoryEventName
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.EventUtils.determineEventType
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

@Service
class HistoryService {
    fun processBlockEvents(
        events: List<Pair<IndexedEvent, GenericEventParameters>>,
        block: Block,
    ): List<IndexedHistoryEvent> {
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

        historyEvents.addAll(getMissingTransactions(block, processedTxs))
        return historyEvents
    }

    private fun processBatchTransferEvents(
        event: Pair<IndexedEvent, GenericEventParameters>
    ): List<IndexedHistoryEvent> {
        val historyEvents = mutableListOf<IndexedHistoryEvent>()

        val tokenIds = event.second.params["tokenId"] as? List<*> ?: emptyList<Any>()
        val values = event.second.params["value"] as? List<*> ?: emptyList<Any>()

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
    ): IndexedHistoryEvent =
        IndexedHistoryEvent(
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
            value = event.second.params.getAsString("value"),
            tokenId = event.second.params.getAsString("tokenId"),
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
                    txId = tx.id,
                    origin = tx.origin,
                    eventName = HistoryEventName.GENERIC_TX,
                    gasPayer = tx.gasPayer,
                )
            }
}
