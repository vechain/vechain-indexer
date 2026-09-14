package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.timeseries.TimeFrameRow

data class VthoGeneratedByBlock(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val total: BigInteger,
    override val period: TimeFramePeriod,
) : TimeFrameRow
