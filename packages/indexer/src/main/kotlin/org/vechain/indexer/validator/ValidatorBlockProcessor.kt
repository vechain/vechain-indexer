package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("validator", "validator-reward")
@Component
open class ValidatorBlockProcessor(
    private val service: ValidatorBlockService,
    repository: ValidatorBlockRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.VALIDATOR_BLOCK.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VALIDATOR_BLOCK.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException("Expected entry to be an IndexingResult.BlockResult")
        }

        val newRecords = service.processBlock(entry.block, entry.callResults())

        if (newRecords.isNotEmpty()) {
            service.save(newRecords)
        }
    }
}
