package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.Impact

data class UserOverview(
    val wallet: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
    val rankByReward: Long?,
    val rankByActionsRewarded: Long?,
    val uniqueXAppInteractions: List<String>,
)

data class UserAppOverview(
    val wallet: String,
    val appId: String,
    val roundId: Int?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
    val rankByReward: Long?,
    val rankByActionsRewarded: Long?,
)
