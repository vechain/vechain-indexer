package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.springframework.stereotype.Repository
import org.vechain.indexer.BaseIndexedRepository

@Profile("nfts")
@Repository
interface NftRepository : BaseIndexedRepository<IndexedNft, String> {

    @Query("{ 'owner': ?0, 'contractAddress': { \$nin: ?1 }, isBlacklisted: { \$ne: true } }")
    fun findByOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft>

    @Query("{ 'owner': ?0, 'contractAddress': ?1, isBlacklisted: { \$ne: true } }")
    fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedNft>

    @Query("{ 'owner': ?0, 'contractAddress': ?1, 'tokenId': ?2, isBlacklisted: { \$ne: true } }")
    fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable,
    ): Slice<IndexedNft>

    @Aggregation(
        pipeline =
            [
                "{ \$match: { 'owner': ?0, 'contractAddress': { \$nin: ?1 }, isBlacklisted: { \$ne: true } } }",
                "{ \$group: { _id: '\$contractAddress', blockNumber: { \$first: '\$blockNumber' } } }",
                "{ \$project: { _id: 1 } }",
            ]
    )
    fun findContractsByNftOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<String>
}
