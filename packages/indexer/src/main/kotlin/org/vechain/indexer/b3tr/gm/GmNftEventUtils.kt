package org.vechain.indexer.b3tr.gm

import java.math.BigInteger
import java.util.Locale.getDefault
import kotlin.plus
import kotlin.text.toBigInteger
import kotlin.to
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsString

object GmNftEventUtils {

    fun processAllTokenEvents(existing: GmNft?, tokenEvents: List<IndexedEvent>): GmNft {
        require(tokenEvents.isNotEmpty()) { "No events provided" }

        val firstTokenId =
            tokenEvents.first().params.getAsString("tokenId")
                ?: error("Missing tokenId in first event")
        val firstBlockNumber = tokenEvents.first().blockNumber

        require(
            tokenEvents.all {
                it.params.getAsString("tokenId") == firstTokenId &&
                    it.blockNumber == firstBlockNumber
            }
        ) {
            "All events must have the same tokenId and blockNumber"
        }

        val mintEvents = tokenEvents.filter { it.eventType == "B3TR_GmMinted" }

        if (existing == null) {
            require(mintEvents.size == 1) {
                when {
                    mintEvents.isEmpty() -> "No mint event found for tokenId $firstTokenId"
                    else -> "Multiple mint events found for tokenId $firstTokenId"
                }
            }
        } else {
            require(mintEvents.isEmpty()) {
                "Mint event should not be present when existing NFT is provided"
            }
        }

        val startingNft = existing ?: processMintedEvent(mintEvents.first())

        val updatedNft =
            tokenEvents.fold(startingNft) { nft, event ->
                when (event.eventType) {
                    "B3TR_GmTransfer",
                    "B3TR_GmBurned" -> processTransferEvent(event, nft)
                    "B3TR_GmUpgrade" -> processUpgradedEvent(event, nft)
                    "B3TR_GmNodeAttached" -> processNodeAttachedEvent(event, nft)
                    "B3TR_GmNodeDetached" -> processNodeDetachedEvent(event, nft)
                    "B3TR_GmNodeLevel" -> processLevelCheckEvent(event, nft)
                    "B3TR_GmMinted" -> nft // Already processed
                    else -> error("Unknown event type ${event.eventType}")
                }
            }

        return if (updatedNft == existing) existing
        else updatedNft.copy(version = updatedNft.version + 1)
    }

    /**
     * Processes a mint event for a GM NFT, creating a new GmNft instance with the provided event
     * data. This function checks if the event type is valid and extracts the token ID and to from
     * the event parameters. If the event type is not "B3TR_GmMinted", it throws an error.
     *
     * @param event The IndexedEvent representing the mint event.
     * @return A new GmNft instance with the details from the mint event.
     */
    fun processMintedEvent(event: IndexedEvent): GmNft {
        // Check the event type
        if (event.eventType != "B3TR_GmMinted") {
            error("Invalid event type for mint: ${event.eventType}")
        }

        val tokenId =
            event.params.getAsString("tokenId")
                ?: error("Missing 'tokenId' param in event: ${event.id}")
        val owner =
            event.params.getAsString("to") ?: error("Missing 'to' param in event: ${event.id}")
        val level = GmLevelName.EARTH

        return GmNft(
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
    }

    /**
     * Processes an upgrade event for a GM NFT, updating the NFT's level and donation amount. This
     * function checks if the event type is valid and extracts the new level and donation value from
     * the event parameters. If the event type is not "B3TR_GmUpgrade", it throws an error.
     *
     * @param event The IndexedEvent representing the upgrade event.
     * @param existing The existing GmNft to update.
     * @return The updated GmNft with the new level and donation amount.
     */
    fun processUpgradedEvent(event: IndexedEvent, existing: GmNft): GmNft {
        // Check the event type
        if (event.eventType != "B3TR_GmUpgrade") {
            error("Invalid event type for upgrade: ${event.eventType}")
        }

        val newLevelStr =
            event.params.getAsString("newLevel")
                ?: error("Missing 'newLevel' param in event: ${event.id}")
        val gmLevel = GmLevelName.map(newLevelStr.toBigInteger())

        val donationValueStr =
            event.params.getAsString("value")
                ?: error("Missing 'b3trDonated' param in event: ${event.id}")
        val donationValue = donationValueStr.toBigInteger()

        return existing.copy(
            level = gmLevel,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
            b3trDonated = existing.b3trDonated + donationValue,
        )
    }

    /**
     * Processes a node attached event for a GM NFT, updating the NFT's attached node ID and level.
     * This function checks if the event type is valid and extracts the level from the event
     * parameters. If the event type is not "B3TR_GmNodeAttached", it throws an error.
     *
     * @param event The IndexedEvent representing the node attached event.
     * @param existing The existing GmNft to update.
     * @return The updated GmNft with the attached node ID and level set.
     */
    fun processNodeAttachedEvent(event: IndexedEvent, existing: GmNft): GmNft {
        // Check the event type
        if (event.eventType != "B3TR_GmNodeAttached") {
            error("Invalid event type for node attached: ${event.eventType}")
        }

        val levelStr =
            event.params.getAsString("level")
                ?: error("Missing 'level' param in event: ${event.id}")
        val gmLevel = GmLevelName.map(levelStr.toBigInteger())

        val attachedNodeId =
            event.params.getAsString("nodeTokenId")
                ?: error("Missing 'nodeTokenId' param in event: ${event.id}")

        return existing.copy(
            attachedNodeId = attachedNodeId,
            level = gmLevel,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    /**
     * Processes a node detached event for a GM NFT, updating the NFT's attached node ID and level.
     * This function checks if the event type is valid and extracts the level from the event
     * parameters. If the event type is not "B3TR_GmNodeDetached", it throws an error
     *
     * @param event The IndexedEvent representing the node detached event.
     * @param existing The existing GmNft to update.
     * @return The updated GmNft with the attached node ID set to null and the level updated.
     */
    fun processNodeDetachedEvent(event: IndexedEvent, existing: GmNft): GmNft {
        // Check the event type
        if (event.eventType != "B3TR_GmNodeDetached") {
            error("Invalid event type for node detached: ${event.eventType}")
        }

        val levelStr =
            event.params.getAsString("level")
                ?: error("Missing 'level' param in event: ${event.id}")
        val gmLevel = GmLevelName.map(levelStr.toBigInteger())

        return existing.copy(
            attachedNodeId = null,
            level = gmLevel,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    /**
     * Processes a transfer event for a GM NFT, updating the owner and block information. This
     * function checks if the event is a valid transfer event and if the address matches the GM
     * contract address. If not, it returns null.
     *
     * Returning null indicates that the NFT should not be updated.
     *
     * @param event The IndexedEvent representing the transfer.
     * @param existing The existing GmNft to update.
     * @param gmContractAddress The address of the GM contract to validate against.
     * @return The updated GmNft with the new owner and block information, or null if the event is
     *   invalid or does not match the GM contract address.
     */
    fun processTransferEvent(event: IndexedEvent, existing: GmNft): GmNft {
        if (event.eventType !in listOf("B3TR_GmTransfer", "B3TR_GmBurned")) {
            error("Invalid event type or missing address in event: ${event.id}")
        }

        val owner =
            event.params.getAsString("to") ?: error("Missing 'to' param in event: ${event.id}")

        return existing.copy(
            owner = owner,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    /**
     * @param event The IndexedEvent containing the level check parameters.
     * @param existing The existing GmNft to update.
     * @return The updated GmNft with the new level
     *
     * TODO: This function is a bit of a hack. It relies on the user voting to detect their GM level
     *   if they upgraded using a legacy Node. Once we no longer support legacy Nodes, this can be
     *   removed.
     */
    fun processLevelCheckEvent(event: IndexedEvent, existing: GmNft): GmNft {
        val newLevelRaw =
            event.params.getAsString("level")?.toBigInteger()
                ?: error("Missing 'level' param in event: ${event.id}")
        val newLevel = GmLevelName.map(newLevelRaw)
        val oldLevel = existing.level

        // If the level hasn't changed, we don't need to update the NFT
        if (newLevel == oldLevel) return existing

        return existing.copy(
            level = newLevel,
            blockId = event.blockId,
            blockNumber = event.blockNumber,
            blockTimestamp = event.blockTimestamp,
        )
    }

    /**
     * Groups a list of events by their tokenId, normalising the tokenId to ensure consistent
     * formatting.
     *
     * @param events List of IndexedEvent to group by tokenId.
     * @return Map where keys are normalised tokenIds and values are lists of events associated with
     *   each tokenId.
     */
    fun groupByTokenId(events: List<IndexedEvent>): Map<String, List<IndexedEvent>> =
        events
            .map {
                it.params.getAsString("tokenId")?.let { tokenId ->
                    tokenId.lowercase(getDefault()) to it
                } ?: error("Missing tokenId in event: ${it.id}")
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, tokenEvents) -> tokenEvents.sortedBy { it.blockNumber } }

    /**
     * Groups a list of events by their block number.
     *
     * @param events List of IndexedEvent to group.
     * @return Map where keys are blockNumbers and values are lists of events in that block.
     */
    fun groupByBlockNumber(events: List<IndexedEvent>): Map<Long, List<IndexedEvent>> =
        events.groupBy { it.blockNumber }.toSortedMap()
}
