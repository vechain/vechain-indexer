package org.vechain.indexer.nft

import org.bson.Document
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.thor.Address
import org.vechain.indexer.utils.BigIntegerUtils
import org.vechain.indexer.utils.SliceBuilder

@Profile("nfts")
@Service
open class NftService(private val mongoTemplate: MongoTemplate) {
    open fun findOwnedNfts(
        owner: Address,
        contractAddress: Address?,
        tokenId: String?,
        excludeCollections: List<Address>?,
        pageable: Pageable,
    ): Slice<IndexedNft> {
        val criteria = ownerCriteria(owner, excludeCollections)
        if (contractAddress != null) {
            criteria.and(IndexedNft::contractAddress.name).`is`(contractAddress.value)
            tokenId
                ?.takeIf { it.isNotEmpty() }
                ?.let { BigIntegerUtils.fromHexOrDecimal(it).toString(10) }
                ?.let { criteria.and(IndexedNft::tokenId.name).`is`(it) }
        }
        return NftBlacklistFilter.findPage(
            mongoTemplate,
            criteria,
            pageable,
            IndexedNft::class.java,
        )
    }

    // Group first so the blacklist lookup runs once per collection, not once per NFT.
    open fun findContractsByNftOwner(
        owner: Address,
        excludeCollections: List<Address>?,
        pageable: Pageable,
    ): Slice<String> {
        val stages = mutableListOf<AggregationOperation>()
        stages += Aggregation.match(ownerCriteria(owner, excludeCollections))
        stages +=
            Aggregation.group(IndexedNft::contractAddress.name)
                .first(IndexedNft::blockNumber.name)
                .`as`(IndexedNft::blockNumber.name)
        stages += NftBlacklistFilter.excludeBlacklisted("_id")
        if (pageable.sort.isSorted) stages += Aggregation.sort(pageable.sort)
        stages += Aggregation.skip(pageable.offset)
        stages += Aggregation.limit(pageable.pageSize + 1L)
        stages += Aggregation.project("_id")

        val contracts =
            mongoTemplate
                .aggregate(
                    Aggregation.newAggregation(stages),
                    mongoTemplate.getCollectionName(IndexedNft::class.java),
                    Document::class.java,
                )
                .mappedResults
                .map { it.getString("_id") }
        return SliceBuilder.buildResultsSlice(contracts, pageable)
    }

    private fun ownerCriteria(owner: Address, excludeCollections: List<Address>?): Criteria {
        val criteria = Criteria.where(IndexedNft::owner.name).`is`(owner.value)
        if (!excludeCollections.isNullOrEmpty()) {
            criteria.and(IndexedNft::contractAddress.name).nin(excludeCollections.map { it.value })
        }
        return criteria
    }
}
