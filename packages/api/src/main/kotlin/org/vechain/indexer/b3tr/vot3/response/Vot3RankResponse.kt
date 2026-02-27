package org.vechain.indexer.b3tr.vot3.response

import java.math.BigInteger

data class Vot3RankResponse(
    val address: String,
    val balance: BigInteger,
    val rank: Long,
    val totalHolders: Long,
    val topPercentage: Double,
)
