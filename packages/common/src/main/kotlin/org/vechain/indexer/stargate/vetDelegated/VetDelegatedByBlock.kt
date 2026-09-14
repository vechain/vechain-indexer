package org.vechain.indexer.stargate.vetDelegated

import java.math.BigInteger
import org.vechain.indexer.stargate.token.LevelledValue
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.timeseries.TimeFrameRow

data class VetDelegatedByBlock(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    override val total: BigInteger,
    override val byLevel: Map<TokenLevel, BigInteger>,
    val totalNftCount: Long = 0,
    val nftCountByLevel: Map<TokenLevel, Long> = emptyMap(),
    override val period: TimeFramePeriod,
) : TimeFrameRow, LevelledValue<BigInteger> {
    override fun valueForLevel(level: TokenLevel?): BigInteger =
        if (level == null) total else byLevel[level] ?: BigInteger.ZERO

    fun nftCountForLevel(level: TokenLevel?): Long =
        if (level == null) totalNftCount else nftCountByLevel[level] ?: 0L
}
