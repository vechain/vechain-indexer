package org.vechain.indexer.repository

import org.bson.Document
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.aggregation.AggregationOperation
import org.springframework.data.mongodb.core.aggregation.GroupOperation
import org.springframework.data.mongodb.core.aggregation.MatchOperation
import org.springframework.stereotype.Repository
import org.vechain.indexer.model.rest.COUNT_LIMIT

@Repository
open class CountRepository(private val mongoTemplate: MongoTemplate) {

    companion object {
        const val COUNT_FIELD = "count"
    }

    /**
     * Count elements matching query up until COUNT_LIMIT + 1 elements
     * The +1 is used to detect in the same query if there are more results
     * than the configured limit, for performance & security reasons
     */
    open fun getCount(
        collection: Class<*>,
        matchOperations: List<MatchOperation> = emptyList(),
        groupOperation: GroupOperation? = null,
        countLimit: Long = COUNT_LIMIT
    ): Long {
        val aggregationOperations: MutableList<AggregationOperation> = mutableListOf()

        if (matchOperations.isNotEmpty()) matchOperations.forEach { aggregationOperations.add(it) }
        if (groupOperation != null) aggregationOperations.add(groupOperation)
        aggregationOperations.add(Aggregation.limit(countLimit + 1))
        aggregationOperations.add(Aggregation.count().`as`(COUNT_FIELD))

        val countAggregation = Aggregation.newAggregation(aggregationOperations)

        val count = mongoTemplate
            .aggregate(countAggregation, collection, Document::class.java)
            .uniqueMappedResult

        return count?.getInteger(COUNT_FIELD)?.toLong() ?: 0
    }

}