package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.StatefulMongoProcessor
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("nfts")
@Component
open class NftProcessor(
    private val nftService: NftService,
    mongoTemplate: MongoTemplate,
    repository: NftRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    StatefulMongoProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.NFT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NFT.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val nftEvents = entry.events()
        val existing = nftService.getExisting(nftEvents)
        val updated = nftService.parseRecords(nftEvents, existing)
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftService.save(updated, existing)
        }
    }
}
