package org.vechain.indexer.b3tr.action.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-all-time-action-summary")
@Repository
open class CustomAppAllTimeActionSummaryRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomAppAllTimeActionSummaryRepository {

    override fun countByAppIds(appIds: Set<String>): Map<String, Long> {
        if (appIds.isEmpty()) return emptyMap()

        val aggregation =
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("appId").`in`(appIds)),
                Aggregation.group("appId").count().`as`("count"),
                Aggregation.project("count").and("_id").`as`("appId"),
            )

        data class CountResult(val appId: String, val count: Long)

        return mongoTemplate
            .aggregate(aggregation, AppAllTimeActionSummary::class.java, CountResult::class.java)
            .mappedResults
            .associate { it.appId to it.count }
    }
}
