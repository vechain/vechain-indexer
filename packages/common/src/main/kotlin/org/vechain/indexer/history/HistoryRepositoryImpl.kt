package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Component
import org.vechain.indexer.nft.IndexedNft
import org.vechain.indexer.utils.SliceBuilder

@Profile("history")
@Component
open class HistoryRepositoryImpl(private val mongoTemplate: MongoTemplate) {
    open fun findByCriteria(criteria: Criteria, pageable: Pageable): Slice<IndexedHistoryEvent> {
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
            mongoTemplate
                .aggregate(
                    aggregation,
                    IndexedHistoryEvent::class.java,
                    IndexedHistoryEvent::class.java,
                )
                .mappedResults,
            pageable,
        )
    }
}
