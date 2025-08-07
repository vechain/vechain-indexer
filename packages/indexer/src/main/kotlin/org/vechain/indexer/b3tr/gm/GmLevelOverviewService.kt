package org.vechain.indexer.b3tr.gm

import kotlin.collections.plusAssign
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.GmNftEventUtils.groupByTokenId
import org.vechain.indexer.b3tr.gm.repository.GmLevelOverviewRepository
import org.vechain.indexer.event.model.generic.IndexedEvent

@Profile("b3tr", "gm-nft-level-overview")
@Service
open class GmLevelOverviewService(
    private val repository: GmLevelOverviewRepository,
    private val gmLevelOverviewArchive: ArchiveService<GmLevelOverview, GmLevelOverviewArchive>,
    @param:Value("\${business-event.substitutions.GM_NFT_CONTRACT}")
    private val contractAddress: String,
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun processEvents(events: List<IndexedEvent>) {
        val nftUpdates = mutableMapOf<String, GmNft>()
        val existingNFTs = mutableListOf<GmNft>()
        val nftDeletions = mutableListOf<GmNft>()
        val overviewDeltas = mutableMapOf<GmLevelName, LevelOverviewDelta>()

        val eventsByTokenId = groupByTokenId(events)

        //        for ((tokenId, tokenEvents) in eventsByTokenId) {
        //
        //
        //            if (nft != null) existingNFTs.add(nft)
        //            for (event in tokenEvents) {
        //                if (event.eventType == "GM_Burned") {
        //                    nftDeletions.add(nft!!)
        //                    break
        //                }
        //
        //                val result =
        //                    when (event.eventType) {
        //                        "GM_Minted" -> GalaxyMemberUtils.processMintedEvent(event)
        //                        "GM_Upgraded" -> GalaxyMemberUtils.processUpgradedEvent(event,
        // nft)
        //                        "NodeAttached",
        //                        "GM_NodeAttached" ->
        // GalaxyMemberUtils.processNodeAttachedEvent(event, nft)
        //                        "NodeDetached",
        //                        "GM_NodeDetached" ->
        // GalaxyMemberUtils.processNodeDetachedEvent(event, nft)
        //                        "Transfer" ->
        //                            GalaxyMemberUtils.processTransferEvent(event, nft,
        // contractAddress)
        //                        "GM_NodeLevel" -> GalaxyMemberUtils.processLevelCheckEvent(event,
        // nft)
        //                        else -> null to null
        //                    }
        //
        //                val (updatedNft, deltaMap) = result
        //                if (updatedNft != null) nft = updatedNft
        //
        //                deltaMap?.forEach { (level, delta) ->
        //                    val existing = overviewDeltas.getOrPut(level) { LevelOverviewDelta() }
        //                    existing.nftsDiff += delta.nftsDiff
        //                    existing.b3trDelta += delta.b3trDelta
        //                    existing.nodesAttached += delta.nodesAttached
        //                }
        //            }
        //
        //            if (nft != null) {
        //                nftUpdates[nft.id] = nft.copy(version = nft.version + 1)
        //            }
        //        }
        //
        //        // Apply updates
        //        if (nftUpdates.isNotEmpty()) {
        //            gmNftRepository.saveAll(nftUpdates.values)
        //        }
        //
        //        // Apply archives
        //        if (existingNFTs.isNotEmpty()) {
        //            gmNftArchiveService.saveAll(existingNFTs)
        //        }
        //
        //        if (nftDeletions.isNotEmpty()) {
        //            val (deltaMap, deletions) = GalaxyMemberUtils.processBurnEvents(nftDeletions)
        //            gmNftRepository.deleteAllById(deletions)
        //
        //            deltaMap.forEach { (level, delta) ->
        //                val existing = overviewDeltas.getOrPut(level) { LevelOverviewDelta() }
        //                existing.nftsDiff += delta.nftsDiff
        //                existing.nodesAttached += delta.nodesAttached
        //            }
        //        }
        //
        //        // Apply overview diffs
        //        val block = events.last()
        //        val existingOverviews =
        //            gmLevelOverviewRepository.findAllById(overviewDeltas.keys.map { it.name
        // }).associateBy {
        //                GMLevelName.valueOf(it.id)
        //            }
        //
        //        val updatedOverviews =
        //            overviewDeltas.map { (levelName, delta) ->
        //                val existing = existingOverviews[levelName]
        //                existing?.copy(
        //                    nfts = existing.nfts + delta.nftsDiff,
        //                    b3trDonated = existing.b3trDonated + delta.b3trDelta,
        //                    nodeHolders = existing.nodeHolders + delta.nodesAttached,
        //                    blockId = block.blockId,
        //                    blockNumber = block.blockNumber,
        //                    blockTimestamp = block.blockTimestamp,
        //                    version = existing.version + 1,
        //                )
        //                    ?: GMLevelOverview(
        //                        version = 1,
        //                        gmLevel = levelName,
        //                        nfts = delta.nftsDiff,
        //                        b3trDonated = delta.b3trDelta,
        //                        nodeHolders = delta.nodesAttached,
        //                        blockId = block.blockId,
        //                        blockNumber = block.blockNumber,
        //                        blockTimestamp = block.blockTimestamp,
        //                    )
        //            }
        //
        //        if (updatedOverviews.isNotEmpty()) {
        //            gmLevelOverviewRepository.saveAll(updatedOverviews)
        //        }
        //
        //        if (existingOverviews.isNotEmpty()) {
        //            gmLevelOverviewArchiveService.saveAll(existingOverviews.values.toList())
        //        }
    }
}
