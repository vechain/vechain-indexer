package org.vechain.indexer.b3tr.xAlloc

import java.math.BigDecimal
import java.math.BigInteger

/** Response DTO for XAllocation voting results. Contains voting data for apps. */
data class XAllocResultResponse(
    val roundId: Int,
    val appId: String,
    val voters: Long,
    val votesReceived: BigInteger,
) {
    companion object {
        /** Convert an XAllocResult to an XAllocResultResponse. */
        fun from(result: XAllocResult): XAllocResultResponse =
            XAllocResultResponse(
                roundId = result.roundId,
                appId = result.appId,
                voters = result.voters,
                votesReceived = result.votesReceived,
            )
    }
}

/**
 * Response DTO for XAllocation earnings distribution. Contains allocation and earnings data for
 * apps. appId is nullable to support results filtered by roundId only.
 */
data class XAllocEarningsResponse(
    val roundId: Int,
    val appId: String?,
    val totalAmount: BigDecimal,
    val unallocatedAmount: BigDecimal,
    val teamAllocationAmount: BigDecimal,
    val rewardsAllocationAmount: BigDecimal,
) {
    companion object {
        /**
         * Convert an XAllocResult to an XAllocEarningsResponse. Returns null if totalAmount is null
         * (no earnings data available).
         */
        fun from(result: XAllocResult): XAllocEarningsResponse? {
            val totalAmount = result.totalAmount ?: return null
            return XAllocEarningsResponse(
                roundId = result.roundId,
                appId = result.appId,
                totalAmount = totalAmount,
                unallocatedAmount = result.unallocatedAmount ?: BigDecimal.ZERO,
                teamAllocationAmount = result.teamAllocationAmount ?: BigDecimal.ZERO,
                rewardsAllocationAmount = result.rewardsAllocationAmount ?: BigDecimal.ZERO,
            )
        }
    }
}
