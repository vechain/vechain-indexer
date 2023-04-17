package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.model.*

object BlockUtils {

    /**
     * Get all confirmed transactions from a block
     */
    fun confirmedTransactions(block: Block): List<WrappedTransaction> {
        return block.transactions.filter { !it.reverted }
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
    fun getOutputs(block: Block): List<Pair<TxOutputs, WrappedTransaction>> {
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
        tx: WrappedTransaction,
        block: Block,
        outputIndex: Int
    ): List<TransferEvent> {
        return ContractUtils.findTransferEvents(events).mapIndexed { eventIndex, event ->
            TransferEvent(
                id = DigestUtils.sha1Hex("${tx.id}-TOPIC-${outputIndex}-${eventIndex}"),
                blockId = block.id,
                blockNumber = block.blockNumber,
                txId = tx.id,
                from = AddressUtil.decode(event.topics[1]),
                to = AddressUtil.decode(event.topics[2]),
                value = event.data,
                tokenAddress = event.address,
                topics = event.topics,
                isVetTransfer = false
            )
        }
    }

    private fun extractVetTransfers(
        transfers: List<TxTransfer>,
        tx: WrappedTransaction,
        block: Block,
        outputIndex: Int
    ): List<TransferEvent> {
        return transfers.mapIndexed { transferIndex, transfer ->
            TransferEvent(
                id = DigestUtils.sha1Hex("${tx.id}-VET-${outputIndex}-${transferIndex}"),
                blockId = block.id,
                blockNumber = block.blockNumber,
                txId = tx.id,
                from = transfer.sender,
                to = transfer.recipient,
                value = transfer.amount,
                topics = listOf(),
                isVetTransfer = true,
                tokenAddress = null,
            )
        }
    }
}