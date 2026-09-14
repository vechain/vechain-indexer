package org.vechain.indexer.stargate.vthoGenerated

import java.math.BigInteger
import org.vechain.indexer.timeseries.TimeFrameDocument
import org.vechain.indexer.timeseries.TimeFramePeriod

data class VthoGeneratedByBlock(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val total: BigInteger,
    override val period: TimeFramePeriod,
) : TimeFrameDocument
