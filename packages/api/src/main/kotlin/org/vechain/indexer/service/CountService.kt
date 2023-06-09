package org.vechain.indexer.service

import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.GroupOperation
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.stereotype.Service
import org.vechain.indexer.model.rest.COUNT_LIMIT

@Service
open class CountService(private val mongoTemplate: MongoTemplate) {

    /**
     * Count elements matching query up until COUNT_LIMIT + 1 elements
     * The +1 is used to detect in the same query if there are more results
     * than the configured limit, for performance & security reasons
     */
    open fun getCount(
        collectionName: String,
        matchOperation: MatchOperation? = null,
        groupOperation: GroupOperation? = null,
    ): Long {
        val aggregationOperations: MutableList<AggregationOperation> = mutableListOf()

        if (matchOperation != null) aggregationOperations.add(matchOperation)
        if (groupOperation != null) aggregationOperations.add(groupOperation)
        aggregationOperations.add(Aggregation.limit(COUNT_LIMIT + 1))
        aggregationOperations.add(Aggregation.count().`as`("total"))

        val countAggregation = Aggregation.newAggregation(aggregationOperations)

        val total = mongoTemplate
            .aggregate(countAggregation, collectionName, Document::class.java)
            .uniqueMappedResult

        return total?.getInteger("total")?.toLong() ?: 0
    }

}