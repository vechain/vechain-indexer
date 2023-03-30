package org.vechain.indexer.utils

import org.vechain.indexer.model.*

object BlockUtils {

    /**
     * Get all clauses from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getAllClauses(block: Block): List<WrappedClause> {
        return block.transactions.filter { it.reverted == false }.flatMap { tx ->
            tx.clauses.mapIndexed { idx, cl ->
                WrappedClause(
                    block, tx, cl, idx
                )
            }
        }
    }

    /**
     * Get all events from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getEvents(block: Block): List<Pair<TxEvent, WrappedTransaction>> {
        return block.transactions.filter { it.reverted == false }.flatMapIndexed { i, tx ->
            tx.outputs.flatMap { output ->
                output.events.map { event ->
                    Pair(event, tx)
                }
            }
        }
    }

    /**
     * Get all outputs from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getOutputs(block: Block): List<Pair<TxOutputs, WrappedTransaction>> {
        return block.transactions.filter { it.reverted == false }.flatMapIndexed { i, tx ->
            tx.outputs.map { output ->
                Pair(output, tx)
            }
        }
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
    fun getTransferEvents(block: Block): List<TransferEvent> {
        return getOutputs(block).flatMap { (output, tx) ->
            ContractUtils.findTransferEvents(output.events).mapIndexed { i, event ->
                TransferEvent(
                    id = "${tx.id}-${i}",
                    blockId = block.id,
                    blockNumber = block.number,
                    txId = tx.id,
                    clauseIndex = 0,
                    from = ContractUtils.removeTopicPadding(event.topics[1]),
                    to = ContractUtils.removeTopicPadding(event.topics[2]),
                    value = event.data,
                    tokenAddress = event.address,
                    topics = event.topics
                )
            }
        }
    }
}