package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.TransferEventType
import org.vechain.thor.model.*
import org.web3j.utils.Numeric

object BlockUtils {

    /** Get all confirmed transactions from a block */
    private fun confirmedTransactions(block: Block): List<Transaction> {
        return block.transactions.filter { !it.reverted }
    }

    /**
     * Get all clauses from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getAllClauses(block: Block): List<IndexedClause> {
        return confirmedTransactions(block).flatMap { tx ->
            tx.clauses.mapIndexed { idx, cl -> IndexedClause(block, tx, cl, idx) }
        }
    }

    /**
     * Get all outputs from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    private fun getOutputs(block: Block): List<Pair<TxOutputs, Transaction>> {
        return confirmedTransactions(block)
            .flatMap { tx -> tx.outputs.map { output -> Pair(output, tx) } }
            .sortedWith(
                // Sort by txId, then output index. We use indexes to create MongoDB IDs, so this is
                // important.
                compareBy({ it.second.id }, { it.second.outputs.indexOf(it.first) })
            )
    }

    /**
     * Get all TRANSFERS from a block. Eg. "Sent 1 VET"
     *
     * DOES NOT include reverted TXs
     */
    fun getTransfers(block: Block): List<TxTransfer> {
        return getOutputs(block).flatMap { (output) -> output.transfers }
    }

    /**
     * Get all FUNGIBLE & NON-FUNGIBLE transfer events from a block.
     *
     * DOES NOT include reverted TXs
     */
    fun getTransferEventsFromTopics(block: Block): List<IndexedTransferEvent> {
        return getOutputs(block).flatMapIndexed { outputIndex, (output, tx) ->
            extractTopicTransfers(output.events, tx, block, outputIndex)
        }
    }

    /**
     * Get all NON-FUNGIBLE transfer events from a block.
     *
     * DOES NOT include reverted TXs
     */
    fun getNFTTransferEventsFromTopics(block: Block): List<IndexedTransferEvent> {
        val transferEvents = getTransferEventsFromTopics(block)
        return transferEvents.filter {
            it.eventType == TransferEventType.NFT && it.tokenAddress != null
        }
    }

    /** Gets all VET transfers AND transfers from topics */
    fun getAllTransferEvents(block: Block): List<IndexedTransferEvent> {
        return getOutputs(block).flatMapIndexed { outputIndex, (output, tx) ->
            val vetTransfers = extractVetTransfers(output.transfers, tx, block, outputIndex)

            val topicTransfers = extractTopicTransfers(output.events, tx, block, outputIndex)

            vetTransfers + topicTransfers
        }
    }

    fun extractTopicTransfers(
        events: List<TxEvent>,
        tx: Transaction,
        block: Block,
        outputIndex: Int
    ): List<IndexedTransferEvent> {
        return events
            .filter { EventUtils.isTransferEvent(it) }
            .flatMapIndexed { eventIndex, event ->
                val transfers = EventUtils.getEventParams(event)

                transfers.mapIndexed { transferIndex, transfer ->
                    IndexedTransferEvent(
                        id =
                            DigestUtils.sha1Hex(
                                "${tx.id}-TOPIC-${outputIndex}-${eventIndex}-${transferIndex}"
                            ),
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        txId = tx.id,
                        from = transfer.from,
                        to = transfer.to,
                        value = transfer.amount,
                        topics = event.topics,
                        tokenAddress = event.address,
                        tokenId = transfer.tokenId,
                        eventType = transfer.eventType
                    )
                }
            }
    }

    fun extractFungibleTransfers(block: Block): List<IndexedTransferEvent> {
        return getOutputs(block).flatMapIndexed { outputIndex, (output, tx) ->
            output.events
                .filter { EventUtils.isFungibleTransferEvent(it) }
                .mapIndexed { transferIndex, event ->
                    val transfer = EventUtils.getFungibleParameters(event).first()

                    IndexedTransferEvent(
                        id = DigestUtils.sha1Hex("${tx.id}-TOPIC-${outputIndex}-${transferIndex}"),
                        blockId = block.id,
                        blockNumber = block.number,
                        blockTimestamp = block.timestamp,
                        txId = tx.id,
                        from = transfer.from,
                        to = transfer.to,
                        value = transfer.amount,
                        topics = listOf(),
                        tokenAddress = event.address,
                        tokenId = transfer.tokenId,
                        eventType = TransferEventType.FUNGIBLE_TOKEN
                    )
                }
        }
    }

    fun extractVetTransfers(
        transfers: List<TxTransfer>,
        tx: Transaction,
        block: Block,
        outputIndex: Int
    ): List<IndexedTransferEvent> {
        return transfers.mapIndexed { transferIndex, transfer ->
            IndexedTransferEvent(
                id = DigestUtils.sha1Hex("${tx.id}-VET-${outputIndex}-${transferIndex}"),
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                txId = tx.id,
                from = transfer.sender,
                to = transfer.recipient,
                value = Numeric.decodeQuantity(transfer.amount),
                topics = listOf(),
                tokenAddress = null,
                tokenId = null,
                eventType = TransferEventType.VET
            )
        }
    }

    /** Find all events that are contract deployments, paired with their transaction. */
    fun extractMasterChangeEvents(block: Block): List<Triple<TxEvent, Transaction, Clause>> {
        return block.transactions
            .filter { tx -> !tx.reverted }
            .flatMap { tx ->
                tx.outputs.flatMapIndexed { idx, output ->
                    output.events
                        .filter { event -> ContractUtils.isMasterEvent(event) }
                        .map { event -> Triple(event, tx, tx.clauses[idx]) }
                }
            }
    }
}
