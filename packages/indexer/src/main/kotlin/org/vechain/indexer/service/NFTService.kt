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
    ): List<IndexedNFT> =
        data.map {
            val nftId = IdUtils.buildNftId(it)
            val version = existing.find { nft -> nft.id == nftId }?.version?.plus(1) ?: 1

            IndexedNFT(
                id = nftId,
                version = version,
                owner = it.params.getAsString("to")!!,
                contractAddress = it.address!!,
                tokenId = it.params.getAsString("tokenId")!!,
                txId = it.txId,
                blockId = it.blockId,
                blockNumber = it.blockNumber,
                blockTimestamp = it.blockTimestamp,
            )
        }

    open fun getExisting(nftTransfers: List<IndexedEvent>): List<IndexedNFT> =
        nftRepository
            .findAllById(
                nftTransfers.map { IdUtils.buildNftId(it) },
            )
            .toList()
}
