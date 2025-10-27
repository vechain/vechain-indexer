package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.IndexerService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository

@Profile("b3tr", "b3tr-x-alloc")
@Service
open class XAllocService(private val xAllocResultRepository: XAllocResultRepository) :
    IndexerService {
    /**
     * Get the results of XAllocation voting for a specific round.
     *
     * @param roundId Round to filter by.
     */
    open fun getXAllocResults(roundId: Int): List<XAllocResult> =
        xAllocResultRepository.findByRoundId(roundId)

    /**
     * Get aggregated XAllocation results for a specific app across all rounds. Fetches results for
     * the app and aggregates them in memory. Returns a response object with aggregated data and
     * roundId set to null.
     *
     * @param appId App ID to filter by.
     */
    open fun getXAllocResultsAggregatedByAppId(appId: String): XAllocResultResponse? {
        val appResults = xAllocResultRepository.findByAppId(appId)
        if (appResults.isEmpty()) {
            return null
        }

        var totalVoters = 0L
        var totalVotes = BigInteger.ZERO
        var totalAmount: BigDecimal? = null
        var unallocatedAmount: BigDecimal? = null
        var teamAllocationAmount: BigDecimal? = null
        var rewardsAllocationAmount: BigDecimal? = null

        for (result in appResults) {
            totalVoters += result.voters
            totalVotes = totalVotes.add(result.totalVotes)

            val existingTotalAmount = totalAmount
            val resultTotalAmount = result.totalAmount
            totalAmount =
                if (existingTotalAmount != null && resultTotalAmount != null) {
                    existingTotalAmount.add(resultTotalAmount)
                } else if (resultTotalAmount != null) {
                    resultTotalAmount
                } else {
                    existingTotalAmount
                }

            val existingUnallocatedAmount = unallocatedAmount
            val resultUnallocatedAmount = result.unallocatedAmount
            unallocatedAmount =
                if (existingUnallocatedAmount != null && resultUnallocatedAmount != null) {
                    existingUnallocatedAmount.add(resultUnallocatedAmount)
                } else if (resultUnallocatedAmount != null) {
                    resultUnallocatedAmount
                } else {
                    existingUnallocatedAmount
                }

            val existingTeamAllocationAmount = teamAllocationAmount
            val resultTeamAllocationAmount = result.teamAllocationAmount
            teamAllocationAmount =
                if (existingTeamAllocationAmount != null && resultTeamAllocationAmount != null) {
                    existingTeamAllocationAmount.add(resultTeamAllocationAmount)
                } else if (resultTeamAllocationAmount != null) {
                    resultTeamAllocationAmount
                } else {
                    existingTeamAllocationAmount
                }

            val existingRewardsAllocationAmount = rewardsAllocationAmount
            val resultRewardsAllocationAmount = result.rewardsAllocationAmount
            rewardsAllocationAmount =
                if (
                    existingRewardsAllocationAmount != null && resultRewardsAllocationAmount != null
                ) {
                    existingRewardsAllocationAmount.add(resultRewardsAllocationAmount)
                } else if (resultRewardsAllocationAmount != null) {
                    resultRewardsAllocationAmount
                } else {
                    existingRewardsAllocationAmount
                }
        }

        return XAllocResultResponse(
            roundId = null,
            appId = appId,
            voters = totalVoters,
            totalVotes = totalVotes,
            totalAmount = totalAmount,
            unallocatedAmount = unallocatedAmount,
            teamAllocationAmount = teamAllocationAmount,
            rewardsAllocationAmount = rewardsAllocationAmount,
        )
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
    open fun getXAllocResultsByRoundIdAsResponse(roundId: Int): List<XAllocResultResponse> =
        xAllocResultRepository
            .findByRoundId(roundId)
            .map { XAllocResultResponse.from(it) }
            .sortedByDescending { it.totalAmount }

    /**
     * Get aggregated XAllocation results by app across all rounds. Returns a list of
     * XAllocResultResponse objects with roundId set to null for each app, sorted by totalAmount
     * descending.
     */
    open fun getXAllocResultsAggregatedByAllApps(): List<XAllocResultResponse> {
        val allResults = xAllocResultRepository.findAll().toList()
        if (allResults.isEmpty()) {
            return emptyList()
        }

        // Group by appId and aggregate
        val resultsByApp = mutableMapOf<String, XAllocResultResponse>()

        for (result in allResults) {
            val appId = result.appId
            val existing = resultsByApp[appId]

            if (existing == null) {
                resultsByApp[appId] =
                    XAllocResultResponse(
                        roundId = null,
                        appId = appId,
                        voters = result.voters,
                        totalVotes = result.totalVotes,
                        totalAmount = result.totalAmount,
                        unallocatedAmount = result.unallocatedAmount,
                        teamAllocationAmount = result.teamAllocationAmount,
                        rewardsAllocationAmount = result.rewardsAllocationAmount,
                    )
            } else {
                // Aggregate with existing entry
                val existingTotalAmount = existing.totalAmount
                val resultTotalAmount = result.totalAmount
                val aggregatedTotalAmount =
                    if (existingTotalAmount != null && resultTotalAmount != null) {
                        existingTotalAmount.add(resultTotalAmount)
                    } else if (resultTotalAmount != null) {
                        resultTotalAmount
                    } else {
                        existingTotalAmount
                    }

                val existingUnallocatedAmount = existing.unallocatedAmount
                val resultUnallocatedAmount = result.unallocatedAmount
                val aggregatedUnallocatedAmount =
                    if (existingUnallocatedAmount != null && resultUnallocatedAmount != null) {
                        existingUnallocatedAmount.add(resultUnallocatedAmount)
                    } else if (resultUnallocatedAmount != null) {
                        resultUnallocatedAmount
                    } else {
                        existingUnallocatedAmount
                    }

                val existingTeamAllocationAmount = existing.teamAllocationAmount
                val resultTeamAllocationAmount = result.teamAllocationAmount
                val aggregatedTeamAllocationAmount =
                    if (
                        existingTeamAllocationAmount != null && resultTeamAllocationAmount != null
                    ) {
                        existingTeamAllocationAmount.add(resultTeamAllocationAmount)
                    } else if (resultTeamAllocationAmount != null) {
                        resultTeamAllocationAmount
                    } else {
                        existingTeamAllocationAmount
                    }

                val existingRewardsAllocationAmount = existing.rewardsAllocationAmount
                val resultRewardsAllocationAmount = result.rewardsAllocationAmount
                val aggregatedRewardsAllocationAmount =
                    if (
                        existingRewardsAllocationAmount != null &&
                            resultRewardsAllocationAmount != null
                    ) {
                        existingRewardsAllocationAmount.add(resultRewardsAllocationAmount)
                    } else if (resultRewardsAllocationAmount != null) {
                        resultRewardsAllocationAmount
                    } else {
                        existingRewardsAllocationAmount
                    }

                resultsByApp[appId] =
                    XAllocResultResponse(
                        roundId = null,
                        appId = appId,
                        voters = existing.voters + result.voters,
                        totalVotes = existing.totalVotes.add(result.totalVotes),
                        totalAmount = aggregatedTotalAmount,
                        unallocatedAmount = aggregatedUnallocatedAmount,
                        teamAllocationAmount = aggregatedTeamAllocationAmount,
                        rewardsAllocationAmount = aggregatedRewardsAllocationAmount,
                    )
            }
        }

        return resultsByApp.values.toList().sortedByDescending { it.totalAmount }
    }

    override fun getLatestIndexedBlocks(): Map<String, Long> =
        mapOf("XAllocResult" to (xAllocResultRepository.getLatestRecord()?.blockNumber ?: 0))
}
