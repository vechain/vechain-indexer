package org.vechain.indexer.transaction.count

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.TransactionCountSummary
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

@Profile("transactions")
@Service
open class TransactionCountService(private val repository: TransactionCountSummaryRepository) {
    @Volatile private var lastProcessed: TransactionCountSummary? = null

    open fun processBlock(block: Block): TransactionCountSummary {
        val previous = getPreviousSummary(block.number)
        require(previous != null || block.number == 0L) {
            "Previous transaction count summary should exist for block ${block.number}"
        }

        val blockTransactions = block.transactions.size.toBigInteger()
        val blockClauses = block.transactions.sumOf { it.clauses.size }.toBigInteger()

        val summary =
            TransactionCountSummary(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                totalTransactions =
                    (previous?.totalTransactions ?: BigInteger.ZERO) + blockTransactions,
                totalClauses = (previous?.totalClauses ?: BigInteger.ZERO) + blockClauses,
            )

        lastProcessed = summary
        return summary
    }

    internal fun getPreviousSummary(blockNumber: Long): TransactionCountSummary? {
        if (blockNumber == 0L) return null

        val cached = lastProcessed
        if (cached != null && cached.blockNumber == blockNumber - 1) {
            return cached
        }
        return repository.findByIdOrNull((blockNumber - 1).toString())
    }

    open fun save(summary: TransactionCountSummary) {
        repository.save(summary)
    }
}
