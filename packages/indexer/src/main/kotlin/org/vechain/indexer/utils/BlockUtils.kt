package org.vechain.indexer.utils

import org.apache.commons.codec.digest.DigestUtils
import org.vechain.indexer.model.*

object BlockUtils {

    /**
     * Get all confirmed transactions from a block
     */
    fun confirmedTransactions(block: Block): List<WrappedTransaction> {
        return block.transactions.filter { it.reverted == false }
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
     * Get all events from a block, paired with the transaction that created it.
     *
     * DOES NOT include reverted TXs
     */
    fun getEvents(block: Block): List<Pair<TxEvent, WrappedTransaction>> {
        return confirmedTransactions(block).flatMap { tx ->
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
        return confirmedTransactions(block).flatMap { tx ->
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
                    id = DigestUtils.sha1Hex("${tx.id}-${i}"),
                    blockId = block.id,
                    blockNumber = block.blockNumber,
                    txId = tx.id,
                    from = AddressUtil.decode(event.topics[1]),
                    to = AddressUtil.decode(event.topics[2]),
                    value = event.data,
                    tokenAddress = event.address,
                    topics = event.topics
                )
            }
        }
    }

    fun getAllTransactions(block: Block): List<WrappedTransaction> {
        return block.transactions.map { tx ->
            WrappedTransaction(
                id = tx.id,
                blockId = block.id,
                blockNumber = block.blockNumber,
                size = tx.size,
                chainTag = tx.chainTag,
                blockRef = tx.blockRef,
                expiration = tx.expiration,
                clauses = tx.clauses,
                gasPriceCoef = tx.gasPriceCoef,
                gas = tx.gas,
                dependsOn = tx.dependsOn,
                nonce = tx.nonce,
                gasUsed = tx.gasUsed,
                gasPayer = tx.gasPayer,
                paid = tx.paid,
                reward = tx.reward,
                reverted = tx.reverted,
                origin = tx.origin,
                delegator = tx.delegator,
                outputs = tx.outputs
            )
        }

    }
}