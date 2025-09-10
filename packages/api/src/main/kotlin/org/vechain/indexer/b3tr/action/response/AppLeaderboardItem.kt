package org.vechain.indexer.b3tr.action.response

import org.vechain.indexer.b3tr.action.Impact

data class AppLeaderboardItem(
    val appId: String,
    val roundId: Int?,
    val date: String?,
    val totalRewardAmount: Double,
    val actionsRewarded: Long,
    val totalImpact: Impact?,
)
