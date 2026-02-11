package org.vechain.indexer

import kotlin.time.TimeSource
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.model.BlockIdentifier

abstract class BaseProcessor(
    private val repository: BaseIndexedRepository<*, *>,
    private val indexerName: String,
    protected val checkpointService: CheckpointService,
    protected val collectionName: String,
) : IndexerProcessor {

    private var blocksSinceCheckpoint = 0

    abstract suspend fun processEntry(entry: IndexingResult)

    override suspend fun process(entry: IndexingResult) {
        val start = TimeSource.Monotonic.markNow()
        try {
            processEntry(entry)
            maybeUpdateCheckpoint(entry)
            ProcessorMetrics.incrementEventsCounter(indexerName, entry.events().size.toDouble())
        } finally {
            ProcessorMetrics.observeProcessingDuration(indexerName, start.elapsedNow())
        }
    }

    private fun maybeUpdateCheckpoint(entry: IndexingResult) {
        blocksSinceCheckpoint++
        if (blocksSinceCheckpoint >= CheckpointService.CHECKPOINT_INTERVAL) {
            checkpointService.saveCheckpoint(collectionName, entry.latestBlockNumber())
            blocksSinceCheckpoint = 0
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val checkpoint = checkpointService.getCheckpoint(collectionName)
        val latestRecord =
            repository.getLatestRecord()?.let {
                BlockIdentifier(number = it.blockNumber, id = it.blockId)
            }
        return listOfNotNull(latestRecord, checkpoint).maxByOrNull { it.number }
    }

    override fun rollback(blockNumber: Long) {
        checkpointService.deleteCheckpoint(collectionName)
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
    }
}
