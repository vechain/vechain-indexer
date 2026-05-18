package org.vechain.indexer

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.config.metrics.ProcessorMetricsRecorder
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseProcessor(
    private val repository: BaseIndexedRepository<*, *>,
    protected val indexerName: String,
    protected val checkpointService: CheckpointService,
    protected val collectionName: String,
    processorMetrics: ProcessorMetrics,
) : IndexerProcessor {

    protected val startupLogger = LoggerFactory.getLogger(this::class.java)
    private val metricsRecorder = ProcessorMetricsRecorder(indexerName, processorMetrics)

    // Set after each successful processEntry. Reader is the shutdown thread (a different thread
    // from the per-processor coroutine that writes it), hence @Volatile.
    @Volatile private var lastObservedBlock: Long? = null

    abstract suspend fun processEntry(entry: IndexingResult)

    /**
     * Hook invoked from [rollback] to reset any per-processor state that depends on persisted data.
     * Subclasses that hold service-level caches (e.g. a most-recent-record cache) MUST override
     * this and clear those caches, otherwise the cache will desynchronise from the database after
     * rollback and `processEvents` checks against stale state.
     *
     * Thread-safety: the indexer-core runner serialises [process] and [rollback] per processor on a
     * single coroutine (see `IndexerRunner.processGroupBlocks` and `BlockIndexer.handleReorg`), so
     * subclass caches written from [process] and cleared from [resetProcessingState] do not need
     * `@Volatile` or locks — coroutine suspension provides the happens-before.
     */
    protected open fun resetProcessingState() {
        metricsRecorder.reset()
    }

    override suspend fun process(entry: IndexingResult) {
        val start = TimeSource.Monotonic.markNow()
        try {
            assertEventsInBlockOrder(entry.events())
            processEntry(entry)
            val latest = entry.latestBlockNumber()
            lastObservedBlock = latest
            checkpointService.trySaveCheckpoint(collectionName, latest)
            metricsRecorder.recordEvents(entry.events().size)
        } finally {
            metricsRecorder.record(entry, start.elapsedNow())
        }
    }

    /**
     * Updates the in-memory checkpoint cursor after a rollback so a subsequent [flushCheckpoint]
     * cannot overwrite the persisted post-rollback state with a stale higher block. Subclasses that
     * override [rollback] (e.g. [BaseStatefulProcessor]) MUST call this with the same
     * `blockNumber - 1` they wrote to the checkpoint.
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
    fun flushCheckpoint() {
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

    private fun assertEventsInBlockOrder(events: List<IndexedEvent>) {
        for (i in 1 until events.size) {
            check(events[i].blockNumber >= events[i - 1].blockNumber) {
                "$indexerName received out-of-order events at index $i: " +
                    "block ${events[i].blockNumber} follows block ${events[i - 1].blockNumber}"
            }
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val start = TimeSource.Monotonic.markNow()
        val checkpoint = checkpointService.getCheckpoint(collectionName)
        val latestRecord =
            try {
                repository.getLatestRecord()?.let {
                    BlockIdentifier(number = it.blockNumber, id = it.blockId)
                }
            } catch (e: Exception) {
                startupLogger.error(
                    "Failed to get latest record for {} (collection: {})",
                    indexerName,
                    collectionName,
                    e,
                )
                throw e
            }
        val result = listOfNotNull(latestRecord, checkpoint).maxByOrNull { it.number }
        val elapsed = start.elapsedNow()
        if (elapsed > 1.seconds) {
            startupLogger.warn(
                "{}: getLastSyncedBlock for {} took {} and returned {}",
                indexerName,
                collectionName,
                elapsed,
                result?.number,
            )
        } else {
            startupLogger.debug(
                "{}: getLastSyncedBlock for {} completed in {} and returned {}",
                indexerName,
                collectionName,
                elapsed,
                result?.number,
            )
        }
        return result
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        val start = TimeSource.Monotonic.markNow()
        resetProcessingState()
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
        rewindLastObservedBlock(blockNumber - 1)
        val elapsed = start.elapsedNow()
        if (elapsed > 1.seconds) {
            startupLogger.warn(
                "{}: rollback for {} at block {} took {}",
                indexerName,
                collectionName,
                blockNumber,
                elapsed,
            )
        } else {
            startupLogger.info(
                "{}: rollback for {} at block {} completed in {}",
                indexerName,
                collectionName,
                blockNumber,
                elapsed,
            )
        }
    }
}
