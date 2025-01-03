package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.model.NFTArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.IdUtils

@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
    private val nftArchiveService: ArchiveService<IndexedNFT, NFTArchive>
) {

    @Transactional(rollbackFor = [Exception::class])
    open fun update(updated: List<IndexedNFT>, existing: List<IndexedNFT>) {
        if (updated.isNotEmpty()) {
            nftRepository.saveAll(updated)
        }

        if (existing.isNotEmpty()) {
            nftArchiveService.saveAll(existing)
        }
    }

    open fun getExisting(nftTransfers: List<IndexedTransferEvent>): List<IndexedNFT> {
        return nftRepository.findAllById(nftTransfers.map { IdUtils.buildNftId(it) }).toList()
    }
}
