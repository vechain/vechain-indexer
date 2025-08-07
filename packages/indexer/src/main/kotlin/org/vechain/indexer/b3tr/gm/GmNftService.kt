package org.vechain.indexer.b3tr.gm

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.groupByBlockNumberAndTokenId
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.processAllTokenEvents
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("b3tr", "gm-nft")
@Service
open class GmNftService(
    private val gmNftRepository: GmNftRepository,
    private val gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
    @param:Value("\${business-event.substitutions.GM_NFT_CONTRACT}")
    private val contractAddress: String,
) {
    open fun processEvents(events: List<IndexedEvent>): Pair<List<GmNft>, List<GmNft>> {
        if (events.isEmpty()) return emptyList<GmNft>() to emptyList()

        val updatedNfts = mutableMapOf<String, GmNft>()
        val existingNfts = mutableListOf<GmNft>()

        groupByBlockNumberAndTokenId(events).forEach { (tokenId, tokenEvents) ->
            val existing = resolveExistingNft(tokenId, updatedNfts)
            val updated = processAllTokenEvents(existing, tokenEvents)
            existing?.let { existingNfts.add(it) }
            updatedNfts[tokenId] = updated
        }

        return updatedNfts.values.toList() to existingNfts
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
