package org.vechain.indexer.b3tr.richlist.response

data class B3trRankResponse(
    val address: String,
    val rank: Long,
    val totalHolders: Long,
    val topPercentage: Double,
)
