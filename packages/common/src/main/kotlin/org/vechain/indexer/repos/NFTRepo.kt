package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.Query
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.NFT

@Repository
interface NFTRepo : BaseIndexedRepo<NFT>, PagingAndSortingRepository<NFT, String> {

    fun findAllByOwner(owner: String, pageable: Pageable): Page<NFT>

    @Query("{\$and: [{owner: ?0}, {contractAddress: {\$in: ?1}}] }")
    fun findAllByOwnerAndContractAddressIn(
        owner: String,
        contractAddresses: List<String>,
        pageable: Pageable
    ): Page<NFT>

}