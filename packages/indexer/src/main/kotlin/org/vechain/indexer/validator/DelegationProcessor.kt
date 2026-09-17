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

@Profile("delegation")
@Component
open class DelegationProcessor(
    private val service: DelegationService,
    repository: DelegationWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.delegation:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.DELEGATION.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.DELEGATION.NAME,
        version,
        processorMetrics,
        backfill?.create(DelegationIndexes.SET),
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected IndexingResult.BlockResult but got ${entry::class.simpleName}"
        }
        val updated = service.processBlock(entry.block, entry.events())
        if (updated.isNotEmpty()) service.save(updated)
    }

    /** Drops the service's zero-cycle mirror so the next block reloads the rolled-back rows. */
    override fun resetProcessingState() {
        super.resetProcessingState()
        service.invalidateCache()
    }
}
