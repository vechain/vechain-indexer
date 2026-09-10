package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("nfts", "history")
@Component
open class NftBlacklistProcessor(
    private val service: NftBlacklistService,
    repository: NftBlacklistRepository,
    mongoTemplate: MongoTemplate,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.NFT_BLACKLIST.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NFT_BLACKLIST.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val (updated, existing) = service.processBlock(entry.events())
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
