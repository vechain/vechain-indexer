package org.vechain.indexer

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.utils.SliceBuilder

abstract class BlacklistableRepository<T : Any>(
    private val mongoTemplate: MongoTemplate,
    private val entityClass: Class<T>,
) {
    open fun findNotBlacklisted(criteria: Criteria, pageable: Pageable): Slice<T> {
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
        return SliceBuilder.buildResultsSlice(
            mongoTemplate.aggregate(aggregation, entityClass, entityClass).mappedResults,
            pageable,
        )
    }
}
