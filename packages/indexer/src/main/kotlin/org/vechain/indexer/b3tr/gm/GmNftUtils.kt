package org.vechain.indexer.b3tr.gm

import java.math.BigInteger
import kotlin.apply
import kotlin.collections.getOrPut
import kotlin.collections.set
import kotlin.plus
import kotlin.text.lowercase
import kotlin.text.toBigInteger
import kotlin.to
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

object GmNftUtils {
    fun processMintedEvent(event: IndexedEvent): Pair<GmNft, Map<GmLevelName, LevelOverviewDelta>> {
        val tokenId = event.params.getAsString("tokenId")!!
        val owner = event.params.getAsString("owner")!!
        val level = GmLevelName.EARTH

        val nft =
            GmNft(
                version = 0,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                id = tokenId,
                owner = owner,
                level = level,
                b3trDonated = BigInteger.ZERO,
                attachedNodeId = null,
            )

        val delta = LevelOverviewDelta(nftsDiff = 1)

        return nft to mapOf(level to delta, GmLevelName.ALL to delta)
    }

    fun processUpgradedEvent(
        event: IndexedEvent,
        existing: GmNft?,
    ): Pair<GmNft?, Map<GmLevelName, LevelOverviewDelta>?> {
        if (existing == null) return null to null

        val gmLevel = GmLevelName.map(event.params.getAsString("level")!!.toBigInteger())

        val updated =
            existing.copy(
                level = gmLevel,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                b3trDonated =
                    existing.b3trDonated + event.params.getAsString("b3trDonated")!!.toBigInteger(),
            )

        val nodeAttachedImpact = if (existing.attachedNodeId != null) 1L else 0

        return updated to
            mapOf(
                existing.level to
                    LevelOverviewDelta(nftsDiff = -1, nodesAttached = -nodeAttachedImpact),
                updated.level to
                    LevelOverviewDelta(
                        nftsDiff = 1,
                        b3trDelta = updated.b3trDonated,
                        nodesAttached = nodeAttachedImpact,
                    ),
                GmLevelName.ALL to LevelOverviewDelta(b3trDelta = updated.b3trDonated),
            )
    }

    fun processNodeAttachedEvent(
        event: IndexedEvent,
        existing: GmNft?,
    ): Pair<GmNft?, Map<GmLevelName, LevelOverviewDelta>?> {
        if (existing == null) return null to null

        var gmLevel = existing.level
        val existingLevel = existing.level
        if (event.eventType == "GM_NodeAttached") {
            gmLevel = GmLevelName.map(event.params.getAsString("level")!!.toBigInteger())
        }

        val updated =
            existing.copy(
                attachedNodeId = event.params.getAsString("nodeTokenId"),
                level = gmLevel,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )

        val deltas = buildMap {
            if (gmLevel != existingLevel) {
                put(existingLevel, LevelOverviewDelta(nftsDiff = -1))
                put(gmLevel, LevelOverviewDelta(nftsDiff = 1, nodesAttached = 1))
            } else {
                put(gmLevel, LevelOverviewDelta(nodesAttached = 1))
            }
            put(GmLevelName.ALL, LevelOverviewDelta(nodesAttached = 1))
        }

        return updated to deltas
    }

    fun processNodeDetachedEvent(
        event: IndexedEvent,
        existing: GmNft?,
    ): Pair<GmNft?, Map<GmLevelName, LevelOverviewDelta>?> {
        if (existing == null) return null to null

        var gmLevel = existing.level
        val existingLevel = existing.level
        if (event.eventType == "GM_NodeDetached") {
            gmLevel = GmLevelName.map(event.params.getAsString("level")!!.toBigInteger())
        }

        val updated =
            existing.copy(
                attachedNodeId = null,
                level = gmLevel,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )

        val deltas = buildMap {
            if (gmLevel != existingLevel) {
                put(existingLevel, LevelOverviewDelta(nodesAttached = -1, nftsDiff = -1))
                put(gmLevel, LevelOverviewDelta(nftsDiff = 1))
            } else {
                put(gmLevel, LevelOverviewDelta(nodesAttached = -1))
            }
            put(GmLevelName.ALL, LevelOverviewDelta(nodesAttached = -1))
        }

        return updated to deltas
    }

    fun processTransferEvent(
        event: IndexedEvent,
        existing: GmNft?,
        gmContractAddress: String,
    ): Pair<GmNft?, Map<GmLevelName, LevelOverviewDelta>?> {
        if (event.address!!.lowercase() != gmContractAddress.lowercase() || existing == null) {
            return null to null
        }
        val updated =
            existing.copy(
                owner = event.params.getAsString("to")!!,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )

        return updated to emptyMap()
    }

    fun processBurnEvents(
        nfts: List<GmNft>
    ): Pair<Map<GmLevelName, LevelOverviewDelta>, List<String>> {
        val deltas = mutableMapOf<GmLevelName, LevelOverviewDelta>()
        val deletions = mutableListOf<String>()

        for (nft in nfts) {
            val level = nft.level
            deletions.add(nft.id)

            deltas
                .getOrPut(level) { LevelOverviewDelta() }
                .apply {
                    nftsDiff--
                    if (nft.attachedNodeId != null) {
                        nodesAttached--
                    }
                }

            deltas
                .getOrPut(GmLevelName.ALL) { LevelOverviewDelta() }
                .apply {
                    nftsDiff--
                    if (nft.attachedNodeId != null) {
                        nodesAttached--
                    }
                }
        }

        return deltas to deletions
    }

    fun processLevelCheckEvent(
        event: IndexedEvent,
        existing: GmNft?,
    ): Pair<GmNft?, Map<GmLevelName, LevelOverviewDelta>?> {
        if (existing == null) return null to null

        val newLevel = GmLevelName.map(event.params.getAsString("level")!!.toBigInteger())
        val oldLevel = existing.level

        if (newLevel == oldLevel) return null to null

        val updated =
            existing.copy(
                level = newLevel,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )

        val deltas = mutableMapOf<GmLevelName, LevelOverviewDelta>()

        val nodeImpact = if (existing.attachedNodeId != null) 1L else 0

        deltas[oldLevel] = LevelOverviewDelta(nftsDiff = -1, nodesAttached = -nodeImpact)
        deltas[newLevel] = LevelOverviewDelta(nftsDiff = 1, nodesAttached = nodeImpact)

        return updated to deltas
    }
}
