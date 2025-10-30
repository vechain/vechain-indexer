package org.vechain.indexer.nft

import org.bson.Document
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.GroupOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.vechain.indexer.BlacklistableRepository
import org.vechain.indexer.utils.SliceBuilder

open class NftRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
    repo: NftBlacklistRepository,
) : BlacklistableRepository<IndexedNft>(mongoTemplate, repo, IndexedNft::class.java) {

    open fun findByOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft> =
        findNotBlacklisted(
            Criteria.where(IndexedNft::owner.name)
                .`is`(owner)
                .and(IndexedNft::contractAddress.name)
                .`nin`(excludeCollections),
            pageable,
        )

    open fun findByOwnerAndContractAddress(
        owner: String,
        contractAddress: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<IndexedNft> =
        findNotBlacklisted(
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
        findNotBlacklisted(
            Criteria.where(IndexedNft::owner.name)
                .`is`(owner)
                .and(IndexedNft::contractAddress.name)
                .`is`(contractAddress)
                .and(IndexedNft::tokenId.name)
                .`is`(tokenId),
            pageable,
        )

    open fun findContractsByNftOwner(
        owner: String,
        excludeCollections: List<String>,
        pageable: Pageable,
    ): Slice<String> {
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

        val excludeCollectionsOperation =
            Aggregation.match(Criteria.where(CONTRACT_ADDRESS).`nin`(excludeCollections))

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
                excludeCollectionsOperation,
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

        return SliceBuilder.buildResultsSlice(distinctContracts, pageable)
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
