package org.vechain.indexer.wov.marketplace

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
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision

@Profile("wov-marketplace")
@Component
open class WovMarketplaceProcessor(
    private val service: WovMarketplaceService,
    private val repository: WovMarketplaceWriteRepository,
    private val thorClient: ThorClient,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.wov-marketplace:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.WOV_MARKETPLACE.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.WOV_MARKETPLACE.NAME,
        version,
        processorMetrics,
        backfill?.create(WovMarketplaceIndexes.SET),
    ) {

    /** Every entry, empty or not, advances the progress the API answers windows against. */
    override suspend fun processEntry(entry: IndexingResult) {
        val endBlock = entry.latestBlockNumber()
        val endTimestamp =
            entry.events().lastOrNull { it.blockNumber == endBlock }?.blockTimestamp
                ?: thorClient.getBlockUnexpanded(BlockRevision.Number(endBlock)).timestamp
        service.save(service.process(entry.events(), endBlock, endTimestamp))
    }
}
