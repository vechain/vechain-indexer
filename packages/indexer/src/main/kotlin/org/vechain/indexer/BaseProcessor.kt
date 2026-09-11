package org.vechain.indexer

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.config.metrics.ProcessorMetricsRecorder
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.BlockIdentifier

/** Store-agnostic processor: metrics, ordering checks, resume and rollback over [IndexerStore]. */
abstract class BaseProcessor(
    private val store: IndexerStore,
    protected val indexerName: String,
    processorMetrics: ProcessorMetrics,
) : IndexerProcessor {

    protected val startupLogger: Logger = LoggerFactory.getLogger(this::class.java)
    private val metricsRecorder = ProcessorMetricsRecorder(indexerName, processorMetrics)

    abstract suspend fun processEntry(entry: IndexingResult)

    /** Runs after a successful [processEntry] with the newest block the entry covered. */
    protected open fun onProcessed(latestBlockNumber: Long) {}

    // Rollback hook: clear service-level caches here. Single-threaded per processor, so no locks.
    protected open fun resetProcessingState() {
        metricsRecorder.reset()
    }

    /** Called by `IndexManager` on shutdown; stores with a separate progress marker persist it. */
    open fun flushCheckpoint() {}

    override suspend fun process(entry: IndexingResult) {
        val start = TimeSource.Monotonic.markNow()
        try {
            assertEventsInBlockOrder(entry.events())
            processEntry(entry)
            onProcessed(entry.latestBlockNumber())
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
        val result = store.lastSynced()
        logTimed(
            "getLastSyncedBlock",
            start.elapsedNow(),
            "returned ${result?.number}",
            quiet = true,
        )
        return result
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        val start = TimeSource.Monotonic.markNow()
        resetProcessingState()
        store.rollbackFrom(blockNumber)
        logTimed("rollback", start.elapsedNow(), "at block $blockNumber")
    }

    private fun logTimed(
        operation: String,
        elapsed: Duration,
        detail: String,
        quiet: Boolean = false,
    ) {
        if (elapsed > 1.seconds) {
            startupLogger.warn("{}: {} {} took {}", indexerName, operation, detail, elapsed)
        } else if (quiet) {
            startupLogger.debug(
                "{}: {} {} completed in {}",
                indexerName,
                operation,
                detail,
                elapsed,
            )
        } else {
            startupLogger.info("{}: {} {} completed in {}", indexerName, operation, detail, elapsed)
        }
    }
}
