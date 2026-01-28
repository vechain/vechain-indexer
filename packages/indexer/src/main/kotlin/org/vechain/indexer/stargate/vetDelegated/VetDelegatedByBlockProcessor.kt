package org.vechain.indexer.stargate.vetDelegated

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseTimeSeriesProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vet-delegated-by-block")
@Component
open class VetDelegatedByBlockProcessor(
    private val service: VetDelegatedByBlockService,
    private val repository: VetDelegatedByBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseTimeSeriesProcessor<VetDelegatedByBlock>(
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VET_DELEGATED_BY_BLOCK,
    ) {

    override fun getLatestRecord(): VetDelegatedByBlock? = repository.getLatestRecord()

    override fun deleteAllByBlockNumberGreaterThanEqual(blockNumber: Long) =
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            return
        }

        val newRecords = service.processBlock(entry.block)

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
