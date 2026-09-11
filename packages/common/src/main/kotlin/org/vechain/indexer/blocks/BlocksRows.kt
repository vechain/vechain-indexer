package org.vechain.indexer.blocks

import java.math.BigDecimal
import org.vechain.indexer.transaction.IndexedTransaction

/**
 * Cumulative counts up to and including a block; the newest block row serves `/transactions/count`.
 */
data class BlockTotals(
    val totalTransactions: Long,
    val totalClauses: Long,
    val totalRevertedTransactions: Long,
    val totalRevertedClauses: Long,
) {
    fun plus(transactions: List<IndexedTransaction>): BlockTotals {
        val reverted = transactions.filter { it.reverted }
        return BlockTotals(
            totalTransactions = totalTransactions + transactions.size,
            totalClauses = totalClauses + transactions.sumOf { it.clauses.size },
            totalRevertedTransactions = totalRevertedTransactions + reverted.size,
            totalRevertedClauses = totalRevertedClauses + reverted.sumOf { it.clauses.size },
        )
    }

    companion object {
        val ZERO = BlockTotals(0, 0, 0, 0)
    }
}

class BlockRow(
    val number: Long,
    val id: ByteArray,
    val parentId: ByteArray,
    val timestamp: Long,
    val size: Int,
    val gasLimit: Long,
    val gasUsed: Long,
    val beneficiary: ByteArray,
    val signer: ByteArray,
    val totalScore: Long,
    val txsRoot: ByteArray,
    val txsFeatures: Short,
    val stateRoot: ByteArray,
    val receiptsRoot: ByteArray,
    val com: Boolean,
    val baseFeePerGas: BigDecimal?,
    val clauseCount: Int,
    val totalVthoPaid: BigDecimal,
    val totals: BlockTotals,
)

class TransactionRow(
    val id: ByteArray,
    val blockNumber: Long,
    val txIndex: Int,
    val type: Short?,
    val size: Int,
    val chainTag: Short,
    val blockRef: ByteArray,
    val expiration: Long,
    val gasPriceCoef: Short?,
    val gas: Long,
    val maxFeePerGas: BigDecimal?,
    val maxPriorityFeePerGas: BigDecimal?,
    val dependsOn: ByteArray?,
    val nonce: BigDecimal,
    val gasUsed: Long,
    val gasPayer: ByteArray,
    val paid: BigDecimal,
    val reward: BigDecimal,
    val reverted: Boolean,
    val origin: ByteArray,
    val outputCount: Short,
)

class ClauseRow(
    val txId: ByteArray,
    val clauseIndex: Int,
    val blockNumber: Long,
    val toAddress: ByteArray?,
    val value: BigDecimal,
    val data: ByteArray,
)

class EventRow(
    val txId: ByteArray,
    val clauseIndex: Int,
    val eventIndex: Int,
    val address: ByteArray,
    val topics: List<ByteArray>,
    val data: ByteArray,
    val name: String?,
    val params: String?,
) {
    init {
        require(topics.size <= MAX_TOPICS) { "an event carries at most $MAX_TOPICS topics" }
    }

    companion object {
        const val MAX_TOPICS = 5
    }
}

class TransferRow(
    val txId: ByteArray,
    val clauseIndex: Int,
    val transferIndex: Int,
    val sender: ByteArray,
    val recipient: ByteArray,
    val amount: BigDecimal,
)

class TransactionRows(
    val transaction: TransactionRow,
    val clauses: List<ClauseRow>,
    val events: List<EventRow>,
    val transfers: List<TransferRow>,
)
