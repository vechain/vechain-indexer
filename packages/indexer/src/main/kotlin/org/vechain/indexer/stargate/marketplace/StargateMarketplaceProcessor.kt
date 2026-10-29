package org.vechain.indexer.stargate.marketplace

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

@Profile("stargate-marketplace")
@Component
open class StargateMarketplaceProcessor(
    private val service: StargateMarketplaceService,
    repository: StargateMarketplaceWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.stargate-marketplace:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.STARGATE_MARKETPLACE.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.STARGATE_MARKETPLACE.NAME,
        version,
        processorMetrics,
        backfill?.create(StargateMarketplaceIndexes.SET),
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        val changed = service.process(entry.events())
        if (changed.isNotEmpty()) service.save(changed)
    }
}
