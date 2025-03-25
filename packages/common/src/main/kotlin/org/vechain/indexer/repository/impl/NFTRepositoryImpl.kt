package org.vechain.indexer.repository.impl

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedNFT
import org.vechain.indexer.repository.impl.SliceBuilder.buildResultsSlice

@Profile("nft-events")
@Component
open class NFTRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
) {

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Slice<String> {
        val matchOwner = Aggregation.match(Criteria.where(OWNER).`is`(owner))

        val lookupBlacklist =
            Aggregation.lookup(
                "nft_blacklist", // collection to join
                CONTRACT_ADDRESS, // local field
                CONTRACT_ADDRESS, // foreign field
                "blacklistMatch" // output field
            )

        val excludeBlacklisted = Aggregation.match(Criteria.where("blacklistMatch").size(0))

        val groupOperation =
            Aggregation.group(CONTRACT_ADDRESS)
                .first(BLOCK_NUMBER)
                .`as`(BLOCK_NUMBER)
                .first(TX_ID)
                .`as`(TX_ID)
                .first(NFT_ID)
                .`as`(NFT_ID_ALIAS)

        val sort =
            Aggregation.sort(
                Sort.by(
                    pageable.sort.getOrderFor(BLOCK_NUMBER)!!.direction,
                    BLOCK_NUMBER,
                    TX_ID,
                    NFT_ID_ALIAS
                )
            )

        val contractsAggregation =
            Aggregation.newAggregation(
                matchOwner,
                lookupBlacklist,
                excludeBlacklisted,
                groupOperation,
                sort,
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong() + 1)
            )

        val distinctContracts =
            mongoTemplate
                .aggregate(contractsAggregation, NFTS_COLLECTION, Document::class.java)
                .mappedResults
                .map { it["_id"] as String }

        return buildResultsSlice(distinctContracts, pageable)
    }

    open fun findByOwner(owner: String, pageable: Pageable): Slice<IndexedNFT> {
        val criteria = Criteria.where(OWNER).`is`(owner)
        return findFilteredNFTs(criteria, pageable)
    }

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable
    ): Slice<IndexedNFT> {
        val criteria = Criteria.where(OWNER).`is`(owner).and(CONTRACT_ADDRESS).`is`(contractAddress)

        return findFilteredNFTs(criteria, pageable)
    }

    open fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable
    ): Slice<IndexedNFT> {
        val criteria =
            Criteria.where(OWNER)
                .`is`(owner)
                .and(CONTRACT_ADDRESS)
                .`is`(contractAddress)
                .and(TOKEN_ID)
                .`is`(tokenId)

        return findFilteredNFTs(criteria, pageable)
    }

    private fun findFilteredNFTs(baseCriteria: Criteria, pageable: Pageable): Slice<IndexedNFT> {
        val matchBase = Aggregation.match(baseCriteria)

        val lookupBlacklist =
            Aggregation.lookup("blacklist", CONTRACT_ADDRESS, CONTRACT_ADDRESS, "blacklistMatch")

        val excludeBlacklisted = Aggregation.match(Criteria.where("blacklistMatch").size(0))

        val sort = Aggregation.sort(pageable.sort)

        val aggregation =
            Aggregation.newAggregation(
                matchBase,
                lookupBlacklist,
                excludeBlacklisted,
                sort,
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong() + 1)
            )

        val results =
            mongoTemplate
                .aggregate(aggregation, NFTS_COLLECTION, IndexedNFT::class.java)
                .mappedResults

        return buildResultsSlice(results, pageable)
    }

    companion object {
        val NFTS_COLLECTION = IndexedNFT::class.java
        val OWNER = IndexedNFT::owner.name
        val CONTRACT_ADDRESS = IndexedNFT::contractAddress.name
        val BLOCK_NUMBER = IndexedNFT::blockNumber.name
        val TX_ID = IndexedNFT::txId.name
        val TOKEN_ID = IndexedNFT::tokenId.name
        val NFT_ID = IndexedNFT::id.name
        const val NFT_ID_ALIAS = "nftId"
    }
}
