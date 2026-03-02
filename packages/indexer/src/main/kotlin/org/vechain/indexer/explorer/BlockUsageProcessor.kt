package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.explorer.repository.BlockUsageRepository

@Profile("explorer", "block-usage")
@Component
open class BlockUsageProcessor(
    repository: BlockUsageRepository,
    private val service: BlockUsageService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.BLOCK_USAGE.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.BLOCK_USAGE.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException("Expected IndexingResult.BlockResult with full block data")
        }
        val blockUsageRecord = service.processBlock(entry.block)

        service.save(blockUsageRecord)
    }
}
