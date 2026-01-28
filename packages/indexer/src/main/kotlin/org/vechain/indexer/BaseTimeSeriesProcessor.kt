package org.vechain.indexer

import kotlin.time.TimeSource
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

/**
 * Abstract base class for time-series PostgreSQL processors.
 *
 * Handles common boilerplate for metrics collection and block synchronization tracking. Designed
 * for non-versioned time-series data where each block has a single record.
 *
 * @param T The type of time-series document
 */
abstract class BaseTimeSeriesProcessor<T : IndexedDocument>(
    private val indexerVersionService: IndexerVersionService,
    private val indexerName: String,
) : IndexerProcessor {

    /** Returns the latest record from the repository, or null if empty. */
    abstract fun getLatestRecord(): T?

    /** Deletes all records at or after the specified block number. */
    abstract fun deleteAllByBlockNumberGreaterThanEqual(blockNumber: Long)

    /** Process the indexing entry. Subclasses implement the actual processing logic here. */
    abstract suspend fun processEntry(entry: IndexingResult)

    override suspend fun process(entry: IndexingResult) {
        val start = TimeSource.Monotonic.markNow()
        try {
            processEntry(entry)
            ProcessorMetrics.incrementEventsCounter(indexerName, entry.events().size.toDouble())
        } finally {
            ProcessorMetrics.observeProcessingDuration(indexerName, start.elapsedNow())
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val latestRecord =
            getLatestRecord()?.let { BlockIdentifier(number = it.blockNumber, id = it.blockId) }
        val lastProcessedBlock = indexerVersionService.getLastProcessedBlock(indexerName)

        return when {
            latestRecord != null && lastProcessedBlock != null -> {
                if (latestRecord.number <= lastProcessedBlock.number) {
                    lastProcessedBlock
                } else {
                    latestRecord
                }
            }
            latestRecord != null -> latestRecord
            lastProcessedBlock != null -> lastProcessedBlock
            else -> null
        }
    }

    override fun rollback(blockNumber: Long) = deleteAllByBlockNumberGreaterThanEqual(blockNumber)
}
