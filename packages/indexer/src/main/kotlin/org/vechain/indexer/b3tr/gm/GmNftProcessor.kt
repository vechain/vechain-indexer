package org.vechain.indexer.b3tr.gm

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

@Profile("b3tr", "b3tr-gm-nft")
@Component
open class GmNftProcessor(
    private val service: GmNftService,
    repository: GmNftWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.b3tr-gm-nft:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.GM_NFT.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.GM_NFT.NAME,
        version,
        processorMetrics,
        backfill?.create(GmNftIndexes.SET),
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val nfts = service.processEvents(entry.events())
        if (nfts.isNotEmpty()) service.save(nfts)
    }
}
