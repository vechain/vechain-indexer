package org.vechain.indexer

import jakarta.annotation.PostConstruct
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.backfill.BackfillCoordinator
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.thor.model.BlockIdentifier

/**
 * A [BaseProcessor] over one Postgres schema: version check on start, checkpoint after each entry.
 */
abstract class PostgresProcessor(
    protected val store: PostgresIndexerStore,
    indexerName: String,
    private val version: Int,
    processorMetrics: ProcessorMetrics,
    private val backfill: BackfillCoordinator? = null,
) : BaseProcessor(store, indexerName, processorMetrics) {

    init {
        backfill?.ownedBy(indexerName)
    }

    /** Ahead of the entry and outside its transaction, so a rebuild can hold the block here. */
    override suspend fun process(entry: IndexingResult) {
        backfill?.beforeEntry(entry)
        super.process(entry)
    }

    @PostConstruct
    open fun bootstrap() {
        store.ensureVersion(version)
        store.trimToCheckpoint()
    }

    override fun onProcessed(latest: BlockIdentifier) = store.onProcessed(latest)

    override fun flushCheckpoint() = store.flushCheckpoint()

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    override fun rollback(blockNumber: Long) {
        super.rollback(blockNumber)
    }
}
