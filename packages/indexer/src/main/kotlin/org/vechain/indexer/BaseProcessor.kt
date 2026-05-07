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
            checkpointService.trySaveCheckpoint(collectionName, entry.latestBlockNumber())
            metricsRecorder.recordEvents(entry.events().size)
        } finally {
            metricsRecorder.record(entry, start.elapsedNow())
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
