package org.vechain.indexer

import jakarta.annotation.PostConstruct
import org.springframework.transaction.annotation.Transactional
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
) : BaseProcessor(store, indexerName, processorMetrics) {

    @PostConstruct
    open fun bootstrap() {
        store.ensureVersion(version)
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
