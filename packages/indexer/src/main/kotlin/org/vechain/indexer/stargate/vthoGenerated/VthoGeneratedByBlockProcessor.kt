package org.vechain.indexer.stargate.vthoGenerated

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("stargate", "vtho-generated-by-block")
@Component
open class VthoGeneratedByBlockProcessor(
    private val service: VthoGeneratedByBlockService,
    repository: VthoGeneratedByBlockRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.VTHO_GENERATED_BY_BLOCK.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VTHO_GENERATED_BY_BLOCK.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            service.invalidateCache()
            throw IllegalArgumentException("Block cannot be null")
        }

        val newRecord = service.processBlock(entry.block, entry.callResults())

        if (newRecord.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(newRecord) }
        }
    }
}
