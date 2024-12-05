package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.IdUtils

@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
) {

    open fun getExisting(nftTransfers: List<IndexedTransferEvent>): List<IndexedNFT> {
        return nftRepository.findAllById(nftTransfers.map { IdUtils.buildNftId(it) }).toList()
    }
}
