package org.vechain.indexer

import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

abstract class BaseProcessor(
    private val repository: BaseIndexedRepository<*, *>,
    private val indexerVersionService: IndexerVersionService,
    private val indexerName: String,
) : IndexerProcessor {

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
