package org.vechain.indexer.repository.impl

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.GroupOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedNFT

@Profile("nft-events")
@Component
open class NFTRepositoryImpl(
    private val mongoTemplate: MongoTemplate,
) {

    open fun findContractsByNFTOwner(owner: String, pageable: Pageable): Slice<String> {
        val matchOperation = Aggregation.match(Criteria.where(OWNER).`is`(owner))

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
                        NFT_ID_ALIAS
                    )
                ),
                Aggregation.skip((pageable.pageNumber * pageable.pageSize).toLong()),
                // We retrieve an additional element on purpose to detect remaining elements in the
                // next page
                Aggregation.limit(pageable.pageSize.toLong() + 1)
            )
        val distinctContracts =
            mongoTemplate
                .aggregate(contractsAggregation, NFTS_COLLECTION, Document::class.java)
                .mappedResults
                .map { it["_id"] as String }

        val hasNext: Boolean
        var results: List<String> = emptyList()

        if (distinctContracts.isEmpty()) hasNext = false
        else if (distinctContracts.size > pageable.pageSize) {
            hasNext = true
            results = distinctContracts.toMutableList().slice(0 until pageable.pageSize)
        } else {
            hasNext = false
            results = distinctContracts
        }

        return SliceImpl(results, pageable, hasNext)
    }

    companion object {
        val NFTS_COLLECTION = IndexedNFT::class.java
        val OWNER = IndexedNFT::owner.name
        val CONTRACT_ADDRESS = IndexedNFT::contractAddress.name
        val BLOCK_NUMBER = IndexedNFT::blockNumber.name
        val TX_ID = IndexedNFT::txId.name
        val NFT_ID = IndexedNFT::id.name
        const val NFT_ID_ALIAS = "nftId"
    }
}
