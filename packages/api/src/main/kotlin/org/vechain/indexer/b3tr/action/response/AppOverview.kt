package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.Impact

data class AppOverview(
    val appId: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
    val rankByReward: Long?,
    val rankByActionsRewarded: Long?,
    val totalUniqueUserInteractions: Long,
)
