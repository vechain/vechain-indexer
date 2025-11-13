package org.vechain.indexer.stargate

import java.math.BigInteger
import org.vechain.indexer.stargate.token.TokenLevel

data class NftHoldersByBlockDto(
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val total: Long,
    val byLevel: Map<TokenLevel, Long>,
)

data class TotalByBlockDto(
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    val total: BigInteger,
    val byLevel: Map<TokenLevel, BigInteger>,
)
