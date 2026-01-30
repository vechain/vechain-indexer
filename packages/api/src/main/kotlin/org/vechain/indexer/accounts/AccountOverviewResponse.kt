package org.vechain.indexer.accounts

import java.math.BigInteger

/**
 * Response DTO for account overview with VTHO earnings. Includes all VTHO earned fields plus a
 * computed total.
 */
data class AccountOverviewResponse(
    val address: String,
    val firstSeen: Long,
    val lastSeen: Long,
    val transactionsSent: Long,
    val clausesSent: Long,
    val vthoBurned: BigInteger,
    val vthoDelegated: BigInteger,
    val gasUsed: BigInteger,
    val vetSent: BigInteger,
    val vetReceived: BigInteger,
    val vetBalance: BigInteger,
    /** Block rewards from AccountOverview (includes all eras) */
    val vthoBlockRewards: BigInteger,
    val vthoPassiveGeneration: BigInteger,
    val vthoClaimedStargate: BigInteger,
    val vthoEarnedTotal: BigInteger,
) {
    companion object {
        fun from(
            overview: AccountOverview,
            stargateVthoClaimed: BigInteger,
        ): AccountOverviewResponse {
            val vthoEarnedTotal =
                overview.vthoBlockRewards
                    .add(overview.vthoPassiveGeneration)
                    .add(stargateVthoClaimed)

            return AccountOverviewResponse(
                address = overview.address,
                firstSeen = overview.firstSeen,
                lastSeen = overview.lastSeen,
                transactionsSent = overview.transactionsSent,
                clausesSent = overview.clausesSent,
                vthoBurned = overview.vthoBurned,
                vthoDelegated = overview.vthoDelegated,
                gasUsed = overview.gasUsed,
                vetSent = overview.vetSent,
                vetReceived = overview.vetReceived,
                vetBalance = overview.vetBalance,
                vthoBlockRewards = overview.vthoBlockRewards,
                vthoPassiveGeneration = overview.vthoPassiveGeneration,
                vthoClaimedStargate = stargateVthoClaimed,
                vthoEarnedTotal = vthoEarnedTotal,
            )
        }
    }
}
