package org.vechain.indexer.repository.impl

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.GroupOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedNft
import org.vechain.indexer.repository.impl.SliceBuilder.buildResultsSlice

@Profile("nfts")
@Component
open class NftRepositoryImpl(private val mongoTemplate: MongoTemplate) {

    private fun findByCriteria(criteria: Criteria, pageable: Pageable): Slice<IndexedNft> {
        val matchOperation = Aggregation.match(criteria)
        val lookupBlacklistOperation =
            Aggregation.lookup(
                "nft_blacklist",
                IndexedNft::contractAddress.name,
                "_id",
                "blacklistInfo",
            )
        val matchBlacklistOperation =
            Aggregation.match(
                Criteria()
                    .orOperator(
                        Criteria.where("blacklistInfo.isBlacklisted").ne(true),
                        Criteria.where("blacklistInfo").exists(false),
                    )
            )
        val aggregation =
            Aggregation.newAggregation(
                matchOperation,
                lookupBlacklistOperation,
                matchBlacklistOperation,
                Aggregation.sort(pageable.sort),
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong() + 1),
            )
        return buildResultsSlice(
            mongoTemplate
                .aggregate(aggregation, IndexedNft::class.java, IndexedNft::class.java)
                .mappedResults,
            pageable,
        )
    }

    open fun findByOwner(owner: String, pageable: Pageable): Slice<IndexedNft> =
        findByCriteria(Criteria.where(IndexedNft::owner.name).`is`(owner), pageable)

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        pageable: Pageable,
    ): Slice<IndexedNft> =
        findByCriteria(
            Criteria.where(IndexedNft::owner.name)
                .`is`(owner)
                .and(IndexedNft::contractAddress.name)
                .`is`(contractAddress),
            pageable,
        )

    open fun findByOwnerAndContractAddressAndTokenId(
        owner: String,
        contractAddress: String,
        tokenId: String,
        pageable: Pageable,
    ): Slice<IndexedNft> =
        findByCriteria(
            Criteria.where(IndexedNft::owner.name)
                .`is`(owner)
                .and(IndexedNft::contractAddress.name)
                .`is`(contractAddress)
                .and(IndexedNft::tokenId.name)
                .`is`(tokenId),
            pageable,
        )

    open fun findContractsByNftOwner(owner: String, pageable: Pageable): Slice<String> {
        val matchOperation = Aggregation.match(Criteria.where(OWNER).`is`(owner))

        val lookupBlacklistOperation =
            Aggregation.lookup("nft_blacklist", CONTRACT_ADDRESS, "_id", "blacklistInfo")

        val matchBlacklistOperation =
            Aggregation.match(
                Criteria()
                    .orOperator(
                        Criteria.where("blacklistInfo.isBlacklisted").ne(true),
                        Criteria.where("blacklistInfo").exists(false),
                    )
            )

        val groupOperation: GroupOperation =
            Aggregation.group(CONTRACT_ADDRESS)
                .first(BLOCK_NUMBER)
                .`as`(BLOCK_NUMBER)
                .first(TX_ID)
                .`as`(TX_ID)
                .first(NFT_ID)
                .`as`(NFT_ID_ALIAS)

        val contractsAggregation =
            Aggregation.newAggregation(
                matchOperation,
                lookupBlacklistOperation,
                matchBlacklistOperation,
                groupOperation,
                Aggregation.sort(
                    Sort.by(
                        pageable.sort.getOrderFor(BLOCK_NUMBER)!!.direction,
                        BLOCK_NUMBER,
                        TX_ID,
                        NFT_ID_ALIAS,
                    )
                ),
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                Aggregation.limit(pageable.pageSize.toLong() + 1),
            )
        val distinctContracts =
            mongoTemplate
                .aggregate(contractsAggregation, NFTS_COLLECTION, Document::class.java)
                .mappedResults
                .map { it["_id"] as String }

        return buildResultsSlice(distinctContracts, pageable)
    }

    companion object {
        val NFTS_COLLECTION = IndexedNft::class.java
        val OWNER = IndexedNft::owner.name
        val CONTRACT_ADDRESS = IndexedNft::contractAddress.name
        val BLOCK_NUMBER = IndexedNft::blockNumber.name
        val TX_ID = IndexedNft::txId.name
        val NFT_ID = IndexedNft::id.name
        const val NFT_ID_ALIAS = "nftId"
    }
}
