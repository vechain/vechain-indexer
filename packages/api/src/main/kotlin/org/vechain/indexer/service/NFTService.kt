package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.utils.HexUtils


@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
) {

    open fun findByOwner(owner: String, pageable: Pageable): Page<IndexedNFT> {
        return nftRepository.findByOwner(HexUtils.normalise(owner), pageable)
    }

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedNFT> {
        return nftRepository.findByOwnerAndContractAddress(HexUtils.normalise(owner), contractAddress, pageable)
    }

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Page<String> {
        return nftRepository.findContractsByNFTOwner(HexUtils.normalise(owner), pageable)
    }

}
