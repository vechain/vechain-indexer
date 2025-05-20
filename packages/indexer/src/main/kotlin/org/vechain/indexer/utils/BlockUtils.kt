package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.thor.model.*
import org.vechain.indexer.utils.ParamUtils.getAsString

object BlockUtils {
    /** Get all confirmed transactions from a block */
    private fun confirmedTransactions(block: Block): List<Transaction> =
        block.transactions.filter { !it.reverted }

    /**
     * Get all outputs from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getOutputs(block: Block): List<Pair<TxOutputs, Transaction>> =
        confirmedTransactions(block).flatMap { tx -> tx.outputs.map { output -> Pair(output, tx) } }

    fun getAllTransferEvents(events: List<IndexedEvent>): List<IndexedTransferEvent> {
        val transferEvents = mutableListOf<IndexedTransferEvent>()
        events.forEach { event ->
            val eventName = EventUtils.determineTransferType(event.params) ?: return@forEach

            if (event.params.getEventType() == "TransferBatch") {
                transferEvents.addAll(processBatchTransferEvents(event))
            } else {
                transferEvents.add(createIndexedTransferEvent(event, eventName))
            }
        }
        return transferEvents
    }

    private fun processBatchTransferEvents(event: IndexedEvent): List<IndexedTransferEvent> {
        val transferEvents = mutableListOf<IndexedTransferEvent>()

        val tokenIds = event.params.getReturnValues()["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.params.getReturnValues()["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            transferEvents.add(
                IndexedTransferEvent(
                    id = DigestUtils.sha1Hex("${event.id}-$i"),
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                    txId = event.txId,
                    from = event.params.getAsString("from")!!,
                    to = event.params.getAsString("to")!!,
                    value = values.getOrNull(i)?.toString()!!,
                    topics = event.raw!!.topics,
                    tokenAddress = event.address,
                    tokenId = tokenIds.getOrNull(i)?.toString(),
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN,
                )
            )
        }
        return transferEvents
    }

    private fun createIndexedTransferEvent(
        event: IndexedEvent,
        transferEventType: TransferEventType,
    ): IndexedTransferEvent {
        val params = event.params

        val tokenId =
            when (transferEventType) {
                TransferEventType.SEMI_FUNGIBLE_TOKEN -> params.getAsString("id")
                else -> params.getAsString("tokenId")
            }

        val value =
            when (transferEventType) {
                TransferEventType.VET -> params.getAsString("amount")!!
                else -> params.getAsString("value") ?: "1"
            }

        return IndexedTransferEvent(
            id = DigestUtils.sha1Hex(event.id),
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            txId = event.txId,
            from = params.getAsString("from")!!,
            to = params.getAsString("to")!!,
            value = value,
            topics = event.raw?.topics.orEmpty(),
            tokenAddress = event.address,
            tokenId = tokenId,
            eventType = transferEventType,
        )
    }
}
