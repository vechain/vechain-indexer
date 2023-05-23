package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.NFT

@Profile("nft-events")
@Repository
interface NFTRepo : BaseIndexedRepo<NFT>, PagingAndSortingRepository<NFT, String> {

    fun findAllByOwner(owner: String, pageable: Pageable): List<NFT>

    fun findAllByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): List<NFT>

}
