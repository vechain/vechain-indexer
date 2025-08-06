package org.vechain.indexer.b3tr.gm

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.GmNftUtils.processBurnEvents
import org.vechain.indexer.b3tr.gm.GmNftUtils.processLevelCheckEvent
import org.vechain.indexer.b3tr.gm.GmNftUtils.processMintedEvent
import org.vechain.indexer.b3tr.gm.GmNftUtils.processNodeAttachedEvent
import org.vechain.indexer.b3tr.gm.GmNftUtils.processNodeDetachedEvent
import org.vechain.indexer.b3tr.gm.GmNftUtils.processTransferEvent
import org.vechain.indexer.b3tr.gm.GmNftUtils.processUpgradedEvent
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("b3tr", "gm-nft")
open class GmNftService(
    private val gmNftRepository: GmNftRepository,
    private val gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
    @param:Value("\${business-event.substitutions.GM_NFT_CONTRACT}")
    private val contractAddress: String,
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun processEvents(events: List<IndexedEvent>) {
        val nftUpdates = mutableMapOf<String, GmNft>()
        val existingNFTs = mutableListOf<GmNft>()
        val nftDeletions = mutableListOf<GmNft>()
        val overviewDeltas = mutableMapOf<GmLevelName, LevelOverviewDelta>()

        val eventsByTokenId =
            events
                .mapNotNull { it.params.getAsString("tokenId")?.let { tokenId -> tokenId to it } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, tokenEvents) -> tokenEvents.sortedBy { it.blockNumber } }

        for ((tokenId, tokenEvents) in eventsByTokenId) {
            var nft = gmNftRepository.findByIdOrNull(tokenId)

            if (nft != null) existingNFTs.add(nft)
            for (event in tokenEvents) {
                if (event.eventType == "GM_Burned") {
                    nftDeletions.add(nft!!)
                    break
                }

                val result =
                    when (event.eventType) {
                        "GM_Minted" -> processMintedEvent(event)
                        "GM_Upgraded" -> processUpgradedEvent(event, nft)
                        "NodeAttached",
                        "GM_NodeAttached" -> processNodeAttachedEvent(event, nft)
                        "NodeDetached",
                        "GM_NodeDetached" -> processNodeDetachedEvent(event, nft)
                        "Transfer" -> processTransferEvent(event, nft, contractAddress)
                        "GM_NodeLevel" -> processLevelCheckEvent(event, nft)
                        else -> null to null
                    }

                val (updatedNft, deltaMap) = result
                if (updatedNft != null) nft = updatedNft

                deltaMap?.forEach { (level, delta) ->
                    val existing = overviewDeltas.getOrPut(level) { LevelOverviewDelta() }
                    existing.nftsDiff += delta.nftsDiff
                    existing.b3trDelta += delta.b3trDelta
                    existing.nodesAttached += delta.nodesAttached
                }
            }

            if (nft != null) {
                nftUpdates[nft.id] = nft.copy(version = nft.version + 1)
            }
        }

        // Apply updates
        if (nftUpdates.isNotEmpty()) {
            gmNftRepository.saveAll(nftUpdates.values)
        }

        // Apply archives
        if (existingNFTs.isNotEmpty()) {
            gmNftArchiveService.saveAll(existingNFTs)
        }

        if (nftDeletions.isNotEmpty()) {
            val (deltaMap, deletions) = processBurnEvents(nftDeletions)
            gmNftRepository.deleteAllById(deletions)

            deltaMap.forEach { (level, delta) ->
                val existing = overviewDeltas.getOrPut(level) { LevelOverviewDelta() }
                existing.nftsDiff += delta.nftsDiff
                existing.nodesAttached += delta.nodesAttached
            }
        }
    }
}
