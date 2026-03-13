package org.vechain.indexer.b3tr.action.repository

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Repository
import org.vechain.indexer.b3tr.action.AppRoundActionSummary

@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
@Repository
open class CustomAppRoundActionSummaryRepositoryImpl
@Autowired
constructor(private val mongoTemplate: MongoTemplate) : CustomAppRoundActionSummaryRepository {

    override fun countByAppIdAndRoundIdPairs(
        pairs: Set<Pair<String, Int>>
    ): Map<Pair<String, Int>, Long> {
        if (pairs.isEmpty()) return emptyMap()

        val orCriteria =
            pairs.map { (appId, roundId) ->
                Criteria()
                    .andOperator(
                        Criteria.where("appId").`is`(appId),
                        Criteria.where("roundId").`is`(roundId),
                    )
            }

        val aggregation =
            Aggregation.newAggregation(
                Aggregation.match(Criteria().orOperator(*orCriteria.toTypedArray())),
                Aggregation.group("appId", "roundId").count().`as`("count"),
                Aggregation.project("count")
                    .and("_id.appId")
                    .`as`("appId")
                    .and("_id.roundId")
                    .`as`("roundId"),
            )

        data class CountResult(val appId: String, val roundId: Int, val count: Long)

        return mongoTemplate
            .aggregate(aggregation, AppRoundActionSummary::class.java, CountResult::class.java)
            .mappedResults
            .associate { (it.appId to it.roundId) to it.count }
    }
}
