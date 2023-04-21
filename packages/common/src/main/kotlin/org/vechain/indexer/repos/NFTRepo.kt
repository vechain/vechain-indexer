package org.vechain.indexer.repos

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.PagingAndSortingRepository
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.NFT

@Repository
interface NFTRepo : IndexerRepository, PagingAndSortingRepository<NFT, String>, CrudRepository<NFT, String> {

    fun findAllByOwner(owner: String, pageable: Pageable): Page<NFT>

    fun findAllByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Page<NFT>

}