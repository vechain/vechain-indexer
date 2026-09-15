package org.vechain.indexer.b3tr.xAlloc

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocService(private val repository: XAllocResultReadRepository) : IndexerService {

    /**
     * Get XAllocation results for a specific app and round.
     *
     * @param appId App ID to filter by.
     * @param roundId Round ID to filter by.
     */
    open fun getXAllocResultByAppIdAndRoundId(appId: String, roundId: Int): XAllocResultResponse? =
        repository.findByAppIdAndRoundId(appId, roundId)?.let { XAllocResultResponse.from(it) }

    /**
     * Get XAllocation results for a specific round, grouped by app. Returns voting data only,
     * sorted by votesReceived descending.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocResultsByRoundId(roundId: Int): List<XAllocResultResponse> =
        repository.findByRoundId(roundId).map { XAllocResultResponse.from(it) }

    /**
     * Get XAllocation earnings for a specific round and app.
     *
     * @param appId App ID to filter by.
     * @param roundId Round ID to filter by.
     */
    open fun getXAllocEarningsByAppIdAndRoundId(
        appId: String,
        roundId: Int,
    ): XAllocEarningsResponse? =
        repository.findByAppIdAndRoundId(appId, roundId)?.let { XAllocEarningsResponse.from(it) }

    /**
     * Get XAllocation earnings for a specific round, grouped by app. Returns earnings data only,
     * sorted by totalAmount descending.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocEarningsByRoundId(roundId: Int): List<XAllocEarningsResponse> =
        repository.findEarningsByRoundId(roundId).mapNotNull { XAllocEarningsResponse.from(it) }

    /**
     * Get XAllocation earnings for a specific app across all rounds. Returns earnings data for each
     * round as separate records, sorted by roundId ascending then totalAmount descending.
     *
     * @param appId App ID to filter by.
     */
    open fun getXAllocEarningsByAppId(appId: String): List<XAllocEarningsResponse> =
        repository.findEarningsByAppId(appId).mapNotNull { XAllocEarningsResponse.from(it) }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("XAllocResult" to repository.latestBlockNumber())
}
