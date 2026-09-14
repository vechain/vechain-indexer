package org.vechain.indexer.stargate.staking

import org.vechain.indexer.stargate.token.LevelledValue
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.timeseries.TimeFrameDocument
import org.vechain.indexer.timeseries.TimeFramePeriod

data class NftHoldersByBlock(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    override val total: Long,
    override val byLevel: Map<TokenLevel, Long>,
    override val period: TimeFramePeriod,
) : TimeFrameDocument, LevelledValue<Long> {
    override fun valueForLevel(level: TokenLevel?): Long =
        if (level == null) total else byLevel[level] ?: 0L
}
