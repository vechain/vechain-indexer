package org.vechain.indexer

import kotlin.time.TimeSource
import org.vechain.indexer.postgres.PostgresIndexedRepository
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

/**
 * Abstract base class for PostgreSQL-based processors.
 *
 * Handles common boilerplate for metrics collection, block synchronization tracking, and rollback
 * operations. Subclasses only need to implement the actual entry processing logic.
 */
abstract class BasePostgresProcessor(
    private val repository: PostgresIndexedRepository,
    private val indexerVersionService: IndexerVersionService,
    private val indexerName: String,
) : IndexerProcessor {

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
        val latestBlock = repository.getLatestBlockIdentifier()
        val lastProcessedBlock = indexerVersionService.getLastProcessedBlock(indexerName)

        return when {
            latestBlock != null && lastProcessedBlock != null -> {
                if (latestBlock.number <= lastProcessedBlock.number) {
                    lastProcessedBlock
                } else {
                    latestBlock
                }
            }
            latestBlock != null -> latestBlock
            lastProcessedBlock != null -> lastProcessedBlock
            else -> null
        }
    }

    override fun rollback(blockNumber: Long) = repository.rollback(blockNumber)
}
