package org.vechain.indexer.stargate.vetStaked

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseTimeSeriesProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vet-staked-by-block")
@Component
open class VetStakedByBlockProcessor(
    private val service: VetStakedByBlockService,
    private val repository: VetStakedByBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseTimeSeriesProcessor<VetStakedByBlock>(
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VET_STAKED_BY_BLOCK,
    ) {

    override fun getLatestRecord(): VetStakedByBlock? = repository.getLatestRecord()

    override fun deleteAllByBlockNumberGreaterThanEqual(blockNumber: Long) =
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val newRecords = service.processEvents(entry.events())

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
