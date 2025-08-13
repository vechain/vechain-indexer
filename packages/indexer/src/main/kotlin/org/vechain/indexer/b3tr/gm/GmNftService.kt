package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("b3tr", "gm-nft")
@Service
open class GmNftService(
    private val repository: GmNftRepository,
    private val gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
) {
    /**
     * Processes a list of IndexedEvents related to GM NFTs.
     *
     * For each tokenId found in the events:
     * - Loads the existing NFT from the repository (if any) and archives it if updates occur.
     * - Applies each event in order to update the NFT state.
     * - Marks NFTs for deletion if a burn event is detected.
     *
     * After processing all events:
     * - Saves updated NFTs back to the repository.
     * - Archives the previous versions of updated NFTs.
     * - Deletes NFTs marked for removal.
     *
     * @param events A list of IndexedEvent objects representing NFT-related events.
     */
    @Transactional(rollbackFor = [Exception::class])
    open fun processEvents(events: List<IndexedEvent>) {
        val nftUpdates = mutableMapOf<String, GmNft>()
        val existingNFTs = mutableListOf<GmNft>()
        val nftDeletions = mutableListOf<String>()

        val eventsByTokenId = GmNftEventUtils.groupByTokenId(events)
        for ((tokenId, tokenEvents) in eventsByTokenId) {
            var nft = resolveExistingNft(tokenId, nftUpdates)
            if (nft != null) existingNFTs.add(nft)
            for (event in tokenEvents) {
                if (event.eventType == "GM_Burned") {
                    nftDeletions.add(nft!!.id)
                    nft = null
                    break
                }

                nft = GmNftEventUtils.processTokenEvent(event, nft)
            }

            if (nft != null) {
                nftUpdates[nft.id] = nft.copy(version = nft.version + 1)
            }
        }

        save(nftUpdates.values.toList(), existingNFTs)

        if (nftDeletions.isNotEmpty()) {
            repository.deleteAllById(nftDeletions)
        }
    }

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
