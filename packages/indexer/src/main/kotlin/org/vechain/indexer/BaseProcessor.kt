package org.vechain.indexer

import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

abstract class BaseProcessor(
    private val repository: BaseIndexedRepository<*, *>,
    private val indexerVersionService: IndexerVersionService,
    private val indexerName: String,
) : IndexerProcessor {

    abstract fun processEntry(entry: IndexingResult)

    override fun process(entry: IndexingResult) {
        val start = System.nanoTime()
        try {
            processEntry(entry)
            ProcessorMetrics.incrementEventsCounter(indexerName, entry.events().size.toDouble())
            ProcessorMetrics.setBestBlock(indexerName, entry.latestBlockNumber().toDouble())
        } finally {
            val duration = System.nanoTime() - start
            val durationMs = duration.toDouble() / 1_000_000.0
            ProcessorMetrics.observeProcessingDuration(indexerName, durationMs)
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val latestRecords =
            repository.getLatestRecord()?.let {
                BlockIdentifier(number = it.blockNumber, id = it.blockId)
            }
        val lastProcessedBlock = indexerVersionService.getLastProcessedBlock(indexerName)

        return when {
            latestRecords != null && lastProcessedBlock != null -> {
                if (latestRecords.number <= lastProcessedBlock.number) {
                    lastProcessedBlock
                } else {
                    latestRecords
                }
            }
            latestRecords != null -> latestRecords
            lastProcessedBlock != null -> lastProcessedBlock
            else -> null
        }
    }

    override fun rollback(blockNumber: Long) =
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
}
