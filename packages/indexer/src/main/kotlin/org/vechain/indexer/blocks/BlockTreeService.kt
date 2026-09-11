package org.vechain.indexer.blocks

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.IndexedTransaction
import org.vechain.indexer.transaction.TransactionService

/** A block and its transactions, projected exactly as the Mongo indexers projected them. */
data class BlockTree(val block: IndexedBlock, val transactions: List<IndexedTransaction>)

@Profile("blocks")
@Service
open class BlockTreeService(
    private val blocksService: BlocksService,
    private val transactionService: TransactionService,
    private val repository: BlocksWriteRepository,
    @Value("\${indexer.version.blocks:1}") private val version: Int,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    // Previous block's running totals; written and cleared on the processor's single coroutine.
    private var lastSaved: Pair<Long, BlockTotals>? = null

    open fun processBlock(block: Block, events: List<IndexedEvent>): BlockTree =
        BlockTree(
            blocksService.processBlock(block),
            transactionService.processBlock(block, events),
        )

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    open fun save(tree: BlockTree) {
        val number = tree.block.blockNumber
        val totals = previousTotals(number).plus(tree.transactions)
        repository.insert(tree.block, tree.transactions, totals)
        lastSaved = number to totals
    }

    private fun previousTotals(blockNumber: Long): BlockTotals {
        lastSaved?.let { (number, totals) -> if (number == blockNumber - 1) return totals }
        val stored = repository.newestTotals()
        require(stored != null || blockNumber == 0L) {
            "No previous block row before block $blockNumber"
        }
        return stored ?: BlockTotals.ZERO
    }

    open fun resetCache() {
        lastSaved = null
    }

    /** Mirrors the Mongo version check: a raised `indexer.version.blocks` empties the schema. */
    open fun ensureVersion() {
        val stored = repository.storedVersion()
        when {
            stored == null -> {
                logger.info("No blocks indexer version recorded; recording version {}", version)
                repository.recordVersion(version)
            }
            stored < version -> {
                logger.info(
                    "Blocks indexer version {} is below {}; truncating for a resync",
                    stored,
                    version,
                )
                repository.resync(version)
                resetCache()
            }
        }
    }
}
