package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedNFT

@Profile("nft-events")
@Repository
interface NFTRepository : BaseIndexedRepo<IndexedNFT> {

    fun findByOwner(owner: String, pageable: Pageable): Page<IndexedNFT>

    fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<IndexedNFT>

    fun findContractsByNFTOwner(owner: String, pageable: Pageable): Page<String>

}
