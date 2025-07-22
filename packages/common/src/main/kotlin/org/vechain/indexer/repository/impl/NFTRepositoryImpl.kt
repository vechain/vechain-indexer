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
open class NFTRepositoryImpl(private val mongoTemplate: MongoTemplate) {

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Slice<String> {
        val matchOperation =
            Aggregation.match(Criteria.where(OWNER).`is`(owner).and(IS_BLACKLISTED).ne(true))

        val groupOperation: GroupOperation =
            Aggregation.group(CONTRACT_ADDRESS)
                .first(BLOCK_NUMBER)
                .`as`(BLOCK_NUMBER)
                .first(TX_ID)
                .`as`(TX_ID)
                .first(NFT_ID)
                .`as`(NFT_ID_ALIAS)

        // find distinct contracts
        val contractsAggregation =
            Aggregation.newAggregation(
                matchOperation,
                groupOperation,
                // Re-sorting is required here because the group stage does not preserve order, and
                // post-group aliases should be used
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
        val IS_BLACKLISTED = IndexedNft::isBlacklisted.name
        val TX_ID = IndexedNft::txId.name
        val NFT_ID = IndexedNft::id.name
        const val NFT_ID_ALIAS = "nftId"
    }
}
