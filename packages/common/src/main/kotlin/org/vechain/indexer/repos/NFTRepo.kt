package org.vechain.indexer.repos

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedNFT

@Profile("nft-events")
@Repository
interface NFTRepo : BaseIndexedRepo<IndexedNFT>, PagingAndSortingRepository<IndexedNFT, String> {

    fun findAllByOwner(owner: String, pageable: Pageable): Slice<IndexedNFT>

    fun findAllByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Slice<IndexedNFT>

}
