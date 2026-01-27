package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.b3tr.action.ActionSummaryUtils.assertEventTypes
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.PostgresPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils
import org.vechain.indexer.utils.ParamUtils.getAsString
import org.vechain.indexer.utils.buildNftId

@Profile("nfts")
@Service
open class NftService(
    private val nftRepository: NftRepository,
    private val nftPruner: PostgresPruner,
    private val blacklistClient: NftBlacklistClient,
) {
    @Transactional(rollbackFor = [Exception::class])
    open fun save(updated: List<IndexedNft>, existing: List<IndexedNft>) {
        nftRepository.saveAllVersioned(updated, existing)

        // Trigger targeted pruning for entities with prior versions
        if (updated.isNotEmpty()) {
            val latestBlock = updated.maxOf { it.blockNumber }
            val entityIds = existing.filter { it.version > 1 }.map { it.id }
            if (entityIds.isNotEmpty()) {
                nftPruner.run(latestBlock, entityIds)
            }
        }
    }

    open suspend fun parseRecords(
        data: List<IndexedEvent>,
        existing: List<IndexedNft>,
    ): List<IndexedNft> {
        // Pre-index existing records for faster lookup
        val existingById = existing.associateBy { it.id }

        // Group events by NFT ID, keeping only the latest event per NFT
        val latestEventsById =
            data
                .map { event ->
                    val nftId = buildNftId(event)
                    nftId to event
                }
                .groupingBy { it.first }
                .reduce { _, acc, e ->
                    if (e.second.blockNumber > acc.second.blockNumber) e else acc
                }
                .mapValues { it.value.second }

        return latestEventsById.map { (nftId, event) ->
            val version = existingById[nftId]?.version?.plus(1) ?: 1
            val contractAddress =
                event.address ?: error("No contract address in event ${event.txId}")
            IndexedNft(
                id = nftId,
                version = version,
                owner = event.params.getAsString("to")!!,
                contractAddress = contractAddress,
                tokenId = event.params.getAsString("tokenId")!!,
                txId = event.txId,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
                isBlacklisted =
                    blacklistClient.isBlacklisted(
                        contractAddress,
                        BlockDetails(event.blockId, event.blockNumber, event.blockTimestamp),
                    ),
            )
        }
    }

    open fun getExisting(nftTransfers: List<IndexedEvent>): List<IndexedNft> =
        nftRepository.findAllById(nftTransfers.map { buildNftId(it) })

    open fun processBlacklistEvents(events: List<IndexedEvent>) {
        // Should only contain blacklist and whitelist events
        assertEventTypes(events, "NFT_Blacklisted", "NFT_Whitelisted")

        val (blacklistAddresses, whitelistAddresses) = EventUtils.partitionBlacklistEvents(events)

        if (blacklistAddresses.isNotEmpty()) blacklist(blacklistAddresses)
        if (whitelistAddresses.isNotEmpty()) whitelist(whitelistAddresses)
    }

    /** Sets isBlacklisted to true for all NFTs related to the given contract addresses */
    protected fun blacklist(contractAddresses: List<String>) {
        nftRepository.blacklist(contractAddresses)
    }

    protected fun whitelist(contractAddresses: List<String>) {
        nftRepository.whitelist(contractAddresses)
    }
}
