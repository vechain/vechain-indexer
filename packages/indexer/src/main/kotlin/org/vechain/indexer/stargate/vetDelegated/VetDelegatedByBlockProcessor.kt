package org.vechain.indexer.stargate.vetDelegated

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("stargate", "vet-delegated-by-block")
@Component
open class VetDelegatedByBlockProcessor(
    private val service: VetDelegatedByBlockService,
    repository: VetDelegatedByBlockRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.VET_DELEGATED_BY_BLOCK.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VET_DELEGATED_BY_BLOCK.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            return
        }

        val newRecords = service.processBlock(entry.block)

        if (newRecords.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.saveRecords(newRecords) }
        }
    }
}
