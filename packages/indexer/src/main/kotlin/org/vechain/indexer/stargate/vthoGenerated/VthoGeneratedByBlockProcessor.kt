package org.vechain.indexer.stargate.vthoGenerated

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseTimeSeriesProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "vtho-generated-by-block")
@Component
open class VthoGeneratedByBlockProcessor(
    private val service: VthoGeneratedByBlockService,
    private val repository: VthoGeneratedByBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseTimeSeriesProcessor<VthoGeneratedByBlock>(
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.VTHO_GENERATED_BY_BLOCK,
    ) {

    override fun getLatestRecord(): VthoGeneratedByBlock? = repository.getLatestRecord()

    override fun deleteAllByBlockNumberGreaterThanEqual(blockNumber: Long) =
        repository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }

        val newRecord = service.processBlock(entry.block, entry.callResults())

        if (newRecord.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(newRecord) }
        }
    }
}
