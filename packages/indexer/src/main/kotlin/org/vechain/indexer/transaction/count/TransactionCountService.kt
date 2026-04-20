package org.vechain.indexer.transaction.count

import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.TransactionCountSummary
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

@Profile("transactions", "transaction-count")
@Service
open class TransactionCountService(
    private val repository: TransactionCountSummaryRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
) {
    @Volatile private var lastPersistedSummary: TransactionCountSummary? = null
    @Volatile private var lastProcessedBlockNumber: Long? = null

    open fun processBlock(block: Block): ProcessingResult {
        val previous = getPreviousSummary(block.number)
        require(previous != null || block.number == 0L) {
            "Previous transaction count summary should exist for block ${block.number}"
        }

        val blockTransactions = block.transactions.size.toBigInteger()
        val blockClauses = block.transactions.sumOf { it.clauses.size }.toBigInteger()
        val revertedTransactions = block.transactions.count { it.reverted }.toBigInteger()
        val revertedClauses =
            block.transactions.filter { it.reverted }.sumOf { it.clauses.size }.toBigInteger()

        if (
            previous != null &&
                blockTransactions == BigInteger.ZERO &&
                blockClauses == BigInteger.ZERO &&
                revertedTransactions == BigInteger.ZERO &&
                revertedClauses == BigInteger.ZERO
        ) {
            val advanced =
                previous.copy(
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                )
            lastProcessedBlockNumber = block.number
            return ProcessingResult(current = advanced, previous = previous, shouldPersist = false)
        }

        val summary =
            TransactionCountSummary(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                version = (previous?.version ?: 0) + 1,
                totalTransactions =
                    (previous?.totalTransactions ?: BigInteger.ZERO) + blockTransactions,
                totalClauses = (previous?.totalClauses ?: BigInteger.ZERO) + blockClauses,
                totalRevertedTransactions =
                    (previous?.totalRevertedTransactions ?: BigInteger.ZERO) + revertedTransactions,
                totalRevertedClauses =
                    (previous?.totalRevertedClauses ?: BigInteger.ZERO) + revertedClauses,
            )

        return ProcessingResult(current = summary, previous = previous, shouldPersist = true)
    }

    internal fun getPreviousSummary(blockNumber: Long): TransactionCountSummary? {
        if (blockNumber == 0L) return null

        val cachedPersistedSummary = lastPersistedSummary
        val cachedLastProcessedBlockNumber = lastProcessedBlockNumber
        if (
            cachedPersistedSummary != null &&
                cachedLastProcessedBlockNumber != null &&
                cachedLastProcessedBlockNumber == blockNumber - 1
        ) {
            return cachedPersistedSummary
        }
        return repository.findByIdOrNull(TransactionCountSummary.SUMMARY_ID)?.also {
            lastPersistedSummary = it
            lastProcessedBlockNumber = it.blockNumber
        }
    }

    open fun resetCache() {
        lastPersistedSummary = null
        lastProcessedBlockNumber = null
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(summary: TransactionCountSummary, previous: TransactionCountSummary?) {
        saveVersionedDocuments(
            updated = listOf(summary),
            existing = listOfNotNull(previous),
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
        )
        recordPersistedSummary(summary)
    }

    internal fun recordPersistedSummary(summary: TransactionCountSummary) {
        lastPersistedSummary = summary
        lastProcessedBlockNumber = summary.blockNumber
    }

    data class ProcessingResult(
        val current: TransactionCountSummary,
        val previous: TransactionCountSummary?,
        val shouldPersist: Boolean,
    )
}
