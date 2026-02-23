package org.vechain.indexer.nft

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
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
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.NFT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NFT.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Filter out blacklist and whitelist events and handle them separately
        val (blacklistEvents, nftEvents) =
            entry
                .events()
                .partition({
                    it.eventType == "NFT_Blacklisted" || it.eventType == "NFT_Whitelisted"
                })

        if (nftEvents.isNotEmpty()) {

            // Find any existing records
            val existing = nftService.getExisting(nftEvents)

            // Process the updated records
            val updated = nftService.parseRecords(nftEvents, existing)

            // Finally save the updated records and archive the existing ones
            if (updated.isNotEmpty() || existing.isNotEmpty()) {
                withContext(Dispatchers.IO) { nftService.save(updated, existing) }
            }
        }

        if (blacklistEvents.isNotEmpty()) {
            nftService.processBlacklistEvents(blacklistEvents)
        }
    }
}
