package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.ParamUtils.getAsString

@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
    private val nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>,
) {
    @Transactional(rollbackFor = [Exception::class])
    open fun update(
        updated: List<IndexedNFT>,
        existing: List<IndexedNFT>,
    ) {
        if (updated.isNotEmpty()) {
            nftRepository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            nftArchiveService.saveAll(existing)
        }
    }

    open fun parseRecords(
        data: List<IndexedEvent>,
        existing: List<IndexedNFT>,
    ): List<IndexedNFT> {
        // Pre-index existing records for faster lookup
        val existingById = existing.associateBy { it.id }

        // Group events by NFT ID, keeping only the latest event per NFT
        val latestEventsById =
            data
                .map { event ->
                    val nftId = IdUtils.buildNftId(event)
                    nftId to event
                }
                .groupingBy { it.first }
                .reduce { _, acc, e ->
                    if (e.second.blockNumber > acc.second.blockNumber) e else acc
                }
                .mapValues { it.value.second }

        return latestEventsById.map { (nftId, event) ->
            val version = existingById[nftId]?.version?.plus(1) ?: 1

            IndexedNFT(
                id = nftId,
                version = version,
                owner = event.params.getAsString("to")!!,
                contractAddress = event.address!!,
                tokenId = event.params.getAsString("tokenId")!!,
                txId = event.txId,
                blockId = event.blockId,
                blockNumber = event.blockNumber,
                blockTimestamp = event.blockTimestamp,
            )
        }
    }

    open fun getExisting(nftTransfers: List<IndexedEvent>): List<IndexedNFT> =
        nftRepository
            .findAllById(
                nftTransfers.map { IdUtils.buildNftId(it) },
            )
            .toList()
}
