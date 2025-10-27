package org.vechain.indexer.b3tr.xAlloc

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocService(
    private val xAllocResultRepository: XAllocResultRepository,
    private val mongoTemplate: MongoTemplate,
) : IndexerService {
    /**
     * Get the results of XAllocation voting for a specific round.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocResults(roundId: Int): List<XAllocResult> =
        xAllocResultRepository.findByRoundId(roundId)

    /**
     * Get aggregated XAllocation results for a specific app across all rounds using MongoDB
     * aggregation pipeline. Returns a response object with aggregated data and roundId set to null.
     *
     * @param appId App ID to filter by.
     */
    open fun getXAllocResultsAggregatedByAppId(appId: String): XAllocResultResponse? {
        val aggregation =
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("appId").`is`(appId)),
                buildAggregationGroup(),
                buildAggregationProjection(),
            )

        return mongoTemplate
            .aggregate(aggregation, XAllocResult::class.java, XAllocResultResponse::class.java)
            .mappedResults
            .firstOrNull()
    }

    /**
     * Get XAllocation results for a specific app and round.
     *
     * @param appId App ID to filter by.
     * @param roundId Round ID to filter by.
     */
    open fun getXAllocResultByAppIdAndRoundId(appId: String, roundId: Int): XAllocResultResponse? =
        xAllocResultRepository.findByAppIdAndRoundId(appId, roundId)?.let {
            XAllocResultResponse.from(it)
        }

    /**
     * Get XAllocation results for a specific round, grouped by app. Returns a list of
     * XAllocResultResponse objects, one per app, sorted by totalAmount descending.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocResultsByRoundIdAsResponse(roundId: Int): List<XAllocResultResponse> {
        val aggregation =
            Aggregation.newAggregation(
                Aggregation.match(Criteria.where("roundId").`is`(roundId)),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalAmount")),
            )

        return mongoTemplate
            .aggregate(aggregation, XAllocResult::class.java, XAllocResultResponse::class.java)
            .mappedResults
    }

    /**
     * Get aggregated XAllocation results by app across all rounds using MongoDB aggregation
     * pipeline. Returns a list of XAllocResultResponse objects with roundId set to null for each
     * app, sorted by totalAmount descending.
     */
    open fun getXAllocResultsAggregatedByAllApps(): List<XAllocResultResponse> {
        val aggregation =
            Aggregation.newAggregation(
                buildAggregationGroup(),
                Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalAmount")),
                buildAggregationProjection(),
            )

        return mongoTemplate
            .aggregate(aggregation, XAllocResult::class.java, XAllocResultResponse::class.java)
            .mappedResults
    }

    /**
     * Builds the group stage for aggregation pipelines. Groups by appId and sums all numeric fields
     * (voters, totalVotes, and various allocation amounts).
     */
    private fun buildAggregationGroup() =
        Aggregation.group("appId")
            .sum("voters")
            .`as`("voters")
            .sum("totalVotes")
            .`as`("totalVotes")
            .sum("totalAmount")
            .`as`("totalAmount")
            .sum("unallocatedAmount")
            .`as`("unallocatedAmount")
            .sum("teamAllocationAmount")
            .`as`("teamAllocationAmount")
            .sum("rewardsAllocationAmount")
            .`as`("rewardsAllocationAmount")
            .first("appId")
            .`as`("appId")

    /**
     * Builds the projection stage for aggregation pipelines. Projects the aggregated fields and
     * sets roundId to null to indicate aggregation across rounds.
     */
    private fun buildAggregationProjection() =
        Aggregation.project(
                "appId",
                "voters",
                "totalVotes",
                "totalAmount",
                "unallocatedAmount",
                "teamAllocationAmount",
                "rewardsAllocationAmount",
            )
            .andExpression("null")
            .`as`("roundId")

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("XAllocResult" to (xAllocResultRepository.getLatestRecord()?.blockNumber ?: 0))
}
