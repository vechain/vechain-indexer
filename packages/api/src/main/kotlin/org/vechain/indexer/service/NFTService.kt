package org.vechain.indexer.service

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.vechain.indexer.model.Address
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.NFTRepository


@Profile("nft-events")
@Service
open class NFTService(
    private val nftRepository: NFTRepository,
) {

    open fun findByOwner(owner: Address, pageable: Pageable): Page<IndexedNFT> {
        return nftRepository.findByOwner(owner.value, pageable)
    }

    open fun findByOwnerAndContractAddress(
        owner: Address,
        contractAddress: Address,
        pageable: Pageable
    ): Page<IndexedNFT> {
        return nftRepository.findByOwnerAndContractAddress(owner.value, contractAddress.value, pageable)
    }

    open fun findContractsByNFTOwner(owner: Address, pageable: Pageable): Page<String> {
        return nftRepository.findContractsByNFTOwner(owner.value, pageable)
    }

}
