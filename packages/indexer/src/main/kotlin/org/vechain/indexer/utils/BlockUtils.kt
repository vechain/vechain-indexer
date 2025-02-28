package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.event.model.generic.GenericEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.indexer.thor.model.*
import org.vechain.indexer.utils.ParamUtils.getAsString

object BlockUtils {
    /** Get all confirmed transactions from a block */
    private fun confirmedTransactions(block: Block): List<Transaction> =
        block.transactions.filter { !it.reverted }

    /**
     * Get all clauses from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getAllClauses(block: Block): List<IndexedClause> =
        confirmedTransactions(block).flatMap { tx ->
            tx.clauses.mapIndexed { idx, cl -> IndexedClause(block, tx, cl, idx) }
        }

    /**
     * Get all outputs from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getOutputs(block: Block): List<Pair<TxOutputs, Transaction>> =
        confirmedTransactions(block).flatMap { tx -> tx.outputs.map { output -> Pair(output, tx) } }

    fun getAllTransferEvents(
        events: List<Pair<IndexedEvent, GenericEventParameters>>
    ): List<IndexedTransferEvent> {
        val transferEvents = mutableListOf<IndexedTransferEvent>()
        events.forEach { event ->
            val eventName = EventUtils.determineTransferType(event.second) ?: return@forEach

            if (event.second.getEventType() == "TransferBatch") {
                transferEvents.addAll(processBatchTransferEvents(event))
            } else {
                transferEvents.add(createIndexedTransferEvent(event, eventName))
            }
        }
        return transferEvents
    }

    private fun processBatchTransferEvents(
        event: Pair<IndexedEvent, GenericEventParameters>
    ): List<IndexedTransferEvent> {
        val transferEvents = mutableListOf<IndexedTransferEvent>()

        val tokenIds = event.second.params["ids"] as? List<*> ?: emptyList<Any>()
        val values = event.second.params["values"] as? List<*> ?: emptyList<Any>()

        for (i in tokenIds.indices) {
            transferEvents.add(
                IndexedTransferEvent(
                    id = DigestUtils.sha1Hex("${event.first.id}-$i"),
                    blockId = event.first.blockId,
                    blockNumber = event.first.blockNumber,
                    blockTimestamp = event.first.blockTimestamp,
                    txId = event.first.txId,
                    from = event.second.params.getAsString("from")!!,
                    to = event.second.params.getAsString("to")!!,
                    value = values.getOrNull(i)?.toString()!!,
                    topics = event.first.raw!!.topics,
                    tokenAddress = event.first.address,
                    tokenId = tokenIds.getOrNull(i)?.toString(),
                    eventType = TransferEventType.SEMI_FUNGIBLE_TOKEN,
                ),
            )
        }
        return transferEvents
    }

    private fun createIndexedTransferEvent(
        event: Pair<IndexedEvent, GenericEventParameters>,
        transferEventType: TransferEventType,
    ): IndexedTransferEvent {
        val params = event.second.params

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
            id = DigestUtils.sha1Hex(event.first.id),
            blockId = event.first.blockId,
            blockNumber = event.first.blockNumber,
            blockTimestamp = event.first.blockTimestamp,
            txId = event.first.txId,
            from = params.getAsString("from")!!,
            to = params.getAsString("to")!!,
            value = value,
            topics = event.first.raw?.topics.orEmpty(),
            tokenAddress = event.first.address,
            tokenId = tokenId,
            eventType = transferEventType,
        )
    }

    /** Find all events that are contract deployments, paired with their transaction. */
    fun extractMasterChangeEvents(block: Block): List<Triple<TxEvent, Transaction, Clause>> =
        block.transactions
            .filter { tx -> !tx.reverted }
            .flatMap { tx ->
                tx.outputs.flatMapIndexed { idx, output ->
                    output.events
                        .filter { event -> ContractUtils.isMasterEvent(event) }
                        .map { event -> Triple(event, tx, tx.clauses[idx]) }
                }
            }
}
