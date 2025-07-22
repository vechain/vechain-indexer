package org.vechain.indexer.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.IndexedNft

@Profile("nfts")
@Repository
interface NFTRepository : BasePagingAndSortingIndexedRepository<IndexedNft, String> {

    @Query("{ 'owner': ?0, 'isBlacklisted': { \$ne: true } }")
    fun findByOwner(owner: String, pageable: Pageable): Slice<IndexedNft>

    @Query("{ 'owner': ?0, 'contractAddress': ?1, 'isBlacklisted': { \$ne: true } }")
    fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedNft>

    @Query("{ 'owner': ?0, 'contractAddress': ?1, 'tokenId': ?2, 'isBlacklisted': { \$ne: true } }")
    fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable,
    ): Slice<IndexedNft>

    fun findContractsByNFTOwner(owner: String, pageable: Pageable): Slice<String>
}
