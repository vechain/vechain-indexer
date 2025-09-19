package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.groupByTokenId
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.processAllTokenEvents
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-gm-nft")
@Service
open class GmNftService(
    private val repository: GmNftRepository,
    private val gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
) {

    /**
     * Processes a list of IndexedEvents related to GM NFTs and returns a pair of lists:
     * - The first list contains updated GmNft objects to be saved.
     * - The second list contains GmNft objects that should be archived. This method groups events
     *   by block number and token ID, then processes each group to update or create GmNft objects.
     *
     * @param events A list of IndexedEvent objects representing NFT-related events.
     * @return A pair of lists: the first containing updated GmNft objects, the second containing
     *   GmNft objects to be archived.
     */
    open fun processEvents(events: List<IndexedEvent>): Pair<List<GmNft>, List<GmNft>> {
        if (events.isEmpty()) return emptyList<GmNft>() to emptyList()

        val updatedNfts = mutableMapOf<String, GmNft>()
        val archiveNfts = mutableListOf<GmNft>()

        groupByBlock(events).forEach { (_, blockEvents) ->
            groupByTokenId(blockEvents).forEach { (tokenId, tokenEvents) ->
                val existing = resolveExistingNft(tokenId, updatedNfts)
                val updated = processAllTokenEvents(existing, tokenEvents)

                // If the updated record is different from the existing one, update it and archive
                // the old
                if (existing != updated) {
                    existing?.let { archiveNfts.add(it) }
                    updatedNfts[tokenId] = updated
                }
            }
        }

        return updatedNfts.values.toList() to archiveNfts
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<GmNft>, existing: List<GmNft>) {
        // Apply updates
        if (updated.isNotEmpty()) {
            repository.saveAll(updated)
        }

        // Apply archives
        if (existing.isNotEmpty()) {
            gmNftArchiveService.saveAll(existing)
        }
    }

    private fun resolveExistingNft(tokenId: String, cache: Map<String, GmNft>): GmNft? =
        cache[tokenId] ?: repository.findByIdOrNull(tokenId)
}
