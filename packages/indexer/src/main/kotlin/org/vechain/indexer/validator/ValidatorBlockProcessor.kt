package org.vechain.indexer.validator

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.backfill.BackfillCoordinatorFactory
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("validator & validator-reward")
@Component
open class ValidatorBlockProcessor(
    private val service: ValidatorBlockService,
    repository: ValidatorBlockWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.validator-rewards:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.VALIDATOR_BLOCK.COLLECTION,
            repository,
            state,
            checkpointProperties,
        ),
        IndexerNames.VALIDATOR_BLOCK.NAME,
        version,
        processorMetrics,
        backfill?.create(ValidatorBlockIndexes.SET),
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

    override fun resetProcessingState() {
        super.resetProcessingState()
        service.invalidateCache()
    }
}
