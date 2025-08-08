package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.groupByBlockNumber
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.groupByTokenId
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.processAllTokenEvents
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("b3tr", "gm-nft")
@Service
open class GmNftService(
    private val gmNftRepository: GmNftRepository,
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

        groupByBlockNumber(events).forEach { (blockNumber, blockEvents) ->
            groupByTokenId(blockEvents).forEach { (tokenId, tokenEvents) ->
                val existing = resolveExistingNft(tokenId, updatedNfts)
                val updated = processAllTokenEvents(existing, tokenEvents)
                existing?.let { archiveNfts.add(it) }
                updatedNfts[tokenId] = updated
            }
        }

        return updatedNfts.values.toList() to archiveNfts
    }

    @Transactional(rollbackFor = [Exception::class])
    open fun save(updatedNfts: List<GmNft>, existingNFTs: List<GmNft>) {
        // Apply updates
        if (updatedNfts.isNotEmpty()) {
            gmNftRepository.saveAll(updatedNfts)
        }

        // Apply archives
        if (existingNFTs.isNotEmpty()) {
            gmNftArchiveService.saveAll(existingNFTs)
        }
    }

    private fun resolveExistingNft(tokenId: String, cache: Map<String, GmNft>): GmNft? =
        cache[tokenId] ?: gmNftRepository.findByIdOrNull(tokenId)
}
