package org.vechain.indexer.stargate

import java.math.BigInteger
import org.vechain.indexer.stargate.token.TokenLevel

data class NftHoldersDto(val total: Long, val byLevel: Map<TokenLevel, Long>)

data class TotalByBlockDto(
    val total: BigInteger,
    val byLevel: Map<TokenLevel, BigInteger>,
    val totalNftCount: Long = 0,
    val nftCountByLevel: Map<TokenLevel, Long> = emptyMap(),
)

data class TotalByPeriodDto(
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val timeFrame: String,
    val total: BigInteger,
    val hourOfDay: Long,
    val dayOfMonth: Long,
    val weekOfYear: Long,
    val month: Long,
    val year: Long,
)
