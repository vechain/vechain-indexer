package org.vechain.indexer

import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

/** A [BaseProcessor] over one Mongo collection, with the throttled `__checkpoint__` writes. */
abstract class MongoProcessor(
    repository: BaseIndexedRepository<*, *>,
    indexerName: String,
    protected val checkpointService: CheckpointService,
    protected val collectionName: String,
    processorMetrics: ProcessorMetrics,
    store: MongoIndexerStore =
        MongoIndexerStore(repository, checkpointService, collectionName, indexerName),
) : BaseProcessor(store, indexerName, processorMetrics) {

    // Set after each successful processEntry; read by the shutdown thread, hence @Volatile.
    @Volatile private var lastObservedBlock: Long? = null

    override fun onProcessed(latestBlockNumber: Long) {
        lastObservedBlock = latestBlockNumber
        checkpointService.trySaveCheckpoint(collectionName, latestBlockNumber)
    }

    /**
     * Rewinds the checkpoint cursor after a rollback; [rollback] overrides pass `blockNumber - 1`.
     */
    protected fun rewindLastObservedBlock(blockNumber: Long) {
        lastObservedBlock = blockNumber
    }

    /**
     * Force-flush the checkpoint to the most recently processed block, bypassing the throttle.
     * Called on graceful shutdown so a clean restart resumes from the actual last-processed block
     * rather than up to [org.vechain.indexer.config.CheckpointProperties.saveIntervalSeconds]
     * earlier. The post-restart drift this closes is what trips `alignComponents` in indexer-core
     * 10.3+ when siblings in a dependency component land at different persisted positions.
     *
     * No-op if this processor has not yet successfully processed a block in this JVM.
     */
    override fun flushCheckpoint() {
        val block = lastObservedBlock ?: return
        try {
            checkpointService.saveCheckpoint(collectionName, block)
            startupLogger.info(
                "{}: flushed checkpoint for {} at block {} on shutdown",
                indexerName,
                collectionName,
                block,
            )
        } catch (e: Exception) {
            startupLogger.error(
                "Failed to flush checkpoint on shutdown for {} at block {}",
                indexerName,
                block,
                e,
            )
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        super.rollback(blockNumber)
        rewindLastObservedBlock(blockNumber - 1)
    }
}
