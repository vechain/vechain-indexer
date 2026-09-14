package org.vechain.indexer.stargate.vthoClaimed

import java.math.BigInteger
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.timeseries.TimeFrameRow

/** The cumulative claim series; `total` is the non-legacy sum, as the API has always read it. */
data class VthoClaimedByBlock(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val total: BigInteger,
    val legacyRewards: BigInteger,
    override val period: TimeFramePeriod,
) : TimeFrameRow
