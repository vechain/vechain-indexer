package org.vechain.indexer.stargate.vthoClaimed

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseTimeSeriesProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vtho-claimed-by-block")
@Component
open class VthoClaimedByBlockProcessor(
    private val service: VthoClaimedByBlockService,
    private val repository: VthoClaimedByBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseTimeSeriesProcessor<VthoClaimedByBlock>(
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VTHO_CLAIMED_BY_BLOCK,
    ) {

    override fun getLatestRecord(): VthoClaimedByBlock? = repository.getLatestRecord()

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
