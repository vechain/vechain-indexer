package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
import java.math.BigInteger

/**
 * Response DTO for XAllocation results. Used for API responses where roundId may be null
 * (aggregated data across rounds) and appId may be null (aggregated across all apps).
 */
data class XAllocResultResponse(
    val roundId: Int?,
    val appId: String?,
    val voters: Long,
    val totalVotes: BigInteger,
    val totalAmount: BigDecimal?,
    val unallocatedAmount: BigDecimal?,
    val teamAllocationAmount: BigDecimal?,
    val rewardsAllocationAmount: BigDecimal?,
) {
    companion object {
        /** Convert an XAllocResult to an XAllocResultResponse. */
        fun from(result: XAllocResult): XAllocResultResponse =
            XAllocResultResponse(
                roundId = result.roundId,
                appId = result.appId,
                voters = result.voters,
                totalVotes = result.totalVotes,
                totalAmount = result.totalAmount,
                unallocatedAmount = result.unallocatedAmount,
                teamAllocationAmount = result.teamAllocationAmount,
                rewardsAllocationAmount = result.rewardsAllocationAmount,
            )

        /** Create an aggregated response with roundId = null. */
        fun aggregated(
            appId: String,
            voters: Long,
            totalVotes: BigInteger,
            totalAmount: BigDecimal?,
            unallocatedAmount: BigDecimal?,
            teamAllocationAmount: BigDecimal?,
            rewardsAllocationAmount: BigDecimal?,
        ): XAllocResultResponse =
            XAllocResultResponse(
                roundId = null,
                appId = appId,
                voters = voters,
                totalVotes = totalVotes,
                totalAmount = totalAmount,
                unallocatedAmount = unallocatedAmount,
                teamAllocationAmount = teamAllocationAmount,
                rewardsAllocationAmount = rewardsAllocationAmount,
            )
    }
}
