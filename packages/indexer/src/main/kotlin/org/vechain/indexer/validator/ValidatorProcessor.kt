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
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("validator", "history")
@Component
open class ValidatorProcessor(
    private val service: ValidatorService,
    repository: ValidatorWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.validator:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.VALIDATOR.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.VALIDATOR.NAME,
        version,
        processorMetrics,
        backfill?.create(ValidatorIndexes.SET),
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected IndexingResult.BlockResult (full block result) but got ${entry::class.simpleName}"
        }
        val updated = service.processBlock(entry.block, entry.events())
        if (updated.isNotEmpty()) service.save(updated)
    }

    /** Drops the service's in-memory mirror so the next block reloads the rolled-back rows. */
    override fun resetProcessingState() {
        super.resetProcessingState()
        service.invalidateCache()
    }
}
