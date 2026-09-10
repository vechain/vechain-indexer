package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.nft.backfill.NftBlacklistBackfillService
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock
import org.vechain.indexer.utils.ParamUtils.getAsString

/**
 * Tracks which NFT collections the blacklist contract currently flags, one document per collection.
 */
@Profile("nfts", "history")
@Service
open class NftBlacklistService(
    private val repository: NftBlacklistRepository,
    private val mongoTemplate: MongoTemplate,
    private val inlineVersioningProperties: InlineVersioningProperties,
    private val backfillService: NftBlacklistBackfillService,
) {
    companion object {
        const val NFT_BLACKLISTED = "NFTBlacklisted"
        const val NFT_WHITELISTED = "NFTWhitelisted"
        val EVENT_NAMES = listOf(NFT_BLACKLISTED, NFT_WHITELISTED)
    }

    open fun processBlock(
        events: List<IndexedEvent>
    ): Pair<List<NftBlacklist>, List<NftBlacklist>> {
        val relevant = events.filter { it.eventType in EVENT_NAMES }
        if (relevant.isEmpty()) return emptyList<NftBlacklist>() to emptyList()

        val candidateIds =
            relevant.mapNotNull { it.params.getAsString("nft")?.let(NftBlacklist::buildId) }.toSet()
        val preloaded = repository.findAllById(candidateIds).associateBy { it.getDocumentId() }
        val accumulator =
            VersionedDocumentAccumulator<NftBlacklist>(
                findById = { id -> preloaded[id] ?: repository.findByIdOrNull(id) },
                initialVersion = 1,
            )

        groupByBlock(relevant).forEach { (blockDetails, blockEvents) ->
            accumulator.startBlock()
            blockEvents.forEach { event -> applyEvent(event, blockDetails, accumulator) }
        }
        return accumulator.results()
    }

    private fun applyEvent(
        event: IndexedEvent,
        blockDetails: BlockDetails,
        accumulator: VersionedDocumentAccumulator<NftBlacklist>,
    ) {
        val id = event.params.getAsString("nft")?.let(NftBlacklist::buildId) ?: return
        val (existing, nextVersion) = accumulator.resolve(id)
        val updated =
            NftBlacklist(
                id = id,
                isBlacklisted = event.eventType == NFT_BLACKLISTED,
                blockId = blockDetails.blockId,
                blockNumber = blockDetails.blockNumber,
                blockTimestamp = blockDetails.blockTimestamp,
                version = nextVersion,
            )
        accumulator.put(id, existing, updated)
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<NftBlacklist>, existing: List<NftBlacklist>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            mongoTemplate = mongoTemplate,
            blockWindow = inlineVersioningProperties.blockWindow,
            maxVersions = inlineVersioningProperties.maxVersions,
            minVersions = inlineVersioningProperties.minVersions,
        )
        backfillService.enqueue(updated)
    }
}
