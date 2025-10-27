package org.vechain.indexer.b3tr.xAlloc.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.stereotype.Repository
import org.vechain.indexer.BasePagingAndSortingIndexedRepository
import org.vechain.indexer.b3tr.xAlloc.XAllocResult
import org.vechain.indexer.b3tr.xAlloc.XAllocResultResponse

@Profile("b3tr", "b3tr-x-alloc")
@Repository
interface XAllocResultRepository : BasePagingAndSortingIndexedRepository<XAllocResult, String> {
    fun findByRoundId(roundId: Int): List<XAllocResult>

    fun findByAppId(appId: String): List<XAllocResult>

    fun findByAppIdAndRoundId(appId: String, roundId: Int): XAllocResult?

    /**
     * Get aggregated XAllocation results for a specific app across all rounds. Groups by appId,
     * sums all numeric fields, and sets roundId to null.
     *
     * @param appId App ID to aggregate by.
     */
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'appId': ?0 } }",
                "{ '\$group': { " +
                    "'_id': null, " +
                    "'appId': { '\$first': '\$appId' }, " +
                    "'voters': { '\$sum': '\$voters' }, " +
                    "'totalVotes': { '\$sum': '\$totalVotes' }, " +
                    "'totalAmount': { '\$sum': '\$totalAmount' }, " +
                    "'unallocatedAmount': { '\$sum': '\$unallocatedAmount' }, " +
                    "'teamAllocationAmount': { '\$sum': '\$teamAllocationAmount' }, " +
                    "'rewardsAllocationAmount': { '\$sum': '\$rewardsAllocationAmount' } " +
                    "} }",
                "{ '\$sort': { 'totalAmount': -1 } }",
                "{ '\$project': { " +
                    "'_id': 0, " +
                    "'roundId': null, " +
                    "'appId': 1, " +
                    "'voters': 1, " +
                    "'totalVotes': 1, " +
                    "'totalAmount': 1, " +
                    "'unallocatedAmount': 1, " +
                    "'teamAllocationAmount': 1, " +
                    "'rewardsAllocationAmount': 1 " +
                    "} }",
            ]
    )
    fun aggregateByAppId(appId: String): XAllocResultResponse?
}
