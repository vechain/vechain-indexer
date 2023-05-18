package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.model.*
import org.web3j.utils.Numeric

object BlockUtils {

    /**
     * Get all confirmed transactions from a block
     */
    fun confirmedTransactions(block: Block): List<Transaction> {
        return block.transactions
            .map { Transaction(it) }
            .filter { !it.reverted }
    }

    /**
     * Get all clauses from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getAllClauses(block: Block): List<WrappedClause> {
        return confirmedTransactions(block).flatMap { tx ->
            tx.clauses.mapIndexed { idx, cl ->
                WrappedClause(
                    block, tx, cl, idx
                )
            }
        }
    }

    /**
     * Get all outputs from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getOutputs(block: Block): List<Pair<TxOutputs, Transaction>> {
        return confirmedTransactions(block)
            .flatMap { tx ->
                tx.outputs.map { output ->
                    Pair(output, tx)
                }
            }.sortedWith(
                //Sort by txId, then output index. We use indexes to create MongoDB IDs, so this is important.
                compareBy(
                    { it.second.id },
                    { it.second.outputs.indexOf(it.first) }
                )
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
    fun getTransferEventsFromTopics(block: Block): List<TransferEvent> {
        return getOutputs(block).flatMapIndexed { outputIndex, (output, tx) ->
            extractTopicTransfers(output.events, tx, block, outputIndex)
        }
    }

    /**
     * Gets all VET transfers AND transfers from topics
     */
    fun getAllTransferEvents(block: Block): List<TransferEvent> {
        return getOutputs(block).flatMapIndexed { outputIndex, (output, tx) ->

            val vetTransfers = extractVetTransfers(output.transfers, tx, block, outputIndex)

            val topicTransfers = extractTopicTransfers(output.events, tx, block, outputIndex)

            vetTransfers + topicTransfers
        }
    }

    private fun extractTopicTransfers(
        events: List<TxEvent>,
        tx: Transaction,
        block: Block,
        outputIndex: Int
    ): List<TransferEvent> {
        return events
            .filter { EventUtils.isTransferEvent(it) }.flatMapIndexed { eventIndex, event ->
                val transfers = EventUtils.getEventParams(event)

                transfers.mapIndexed { transferIndex, transfer ->
                    TransferEvent(
                        id = DigestUtils.sha1Hex("${tx.id}-TOPIC-${outputIndex}-${eventIndex}-${transferIndex}"),
                        blockId = block.blockId,
                        blockNumber = block.blockNumber,
                        blockTimestamp = block.blockTimestamp, txId = tx.id,
                        from = transfer.from,
                        to = transfer.to,
                        value = transfer.amount,
                        topics = event.topics,
                        tokenAddress = event.address,
                        eventType = transfer.eventType
                    )
                }
            }
    }

    private fun extractVetTransfers(
        transfers: List<TxTransfer>,
        tx: Transaction,
        block: Block,
        outputIndex: Int
    ): List<TransferEvent> {
        return transfers.mapIndexed { transferIndex, transfer ->
            TransferEvent(
                id = DigestUtils.sha1Hex("${tx.id}-VET-${outputIndex}-${transferIndex}"),
                blockId = block.blockId,
                blockNumber = block.blockNumber,
                blockTimestamp = block.blockTimestamp,
                txId = tx.id,
                from = transfer.sender,
                to = transfer.recipient,
                value = Numeric.decodeQuantity(transfer.amount),
                topics = listOf(),
                tokenAddress = null,
                eventType = TransferEventType.VET
            )
        }
    }
}