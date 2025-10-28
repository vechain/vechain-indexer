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
    val votesReceived: BigInteger,
    val votesReceivedQf: BigInteger,
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
                votesReceived = result.votesReceived,
                votesReceivedQf = result.votesReceivedQf,
                totalAmount = result.totalAmount,
                unallocatedAmount = result.unallocatedAmount,
                teamAllocationAmount = result.teamAllocationAmount,
                rewardsAllocationAmount = result.rewardsAllocationAmount,
            )

        /** Create an aggregated response with roundId = null. */
        fun aggregated(
            appId: String,
            voters: Long,
            votesReceived: BigInteger,
            votesReceivedQf: BigInteger,
            totalAmount: BigDecimal?,
            unallocatedAmount: BigDecimal?,
            teamAllocationAmount: BigDecimal?,
            rewardsAllocationAmount: BigDecimal?,
        ): XAllocResultResponse =
            XAllocResultResponse(
                roundId = null,
                appId = appId,
                voters = voters,
                votesReceived = votesReceived,
                votesReceivedQf = votesReceivedQf,
                totalAmount = totalAmount,
                unallocatedAmount = unallocatedAmount,
                teamAllocationAmount = teamAllocationAmount,
                rewardsAllocationAmount = rewardsAllocationAmount,
            )
    }
}
