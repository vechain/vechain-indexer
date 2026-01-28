package org.vechain.indexer.stargate.vthoGenerated

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument

data class VthoGeneratedByBlock(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    override val hourOfDay: Long,
    override val dayOfMonth: Long,
    override val weekOfYear: Long,
    override val month: Long,
    override val year: Long,
    override val timeFrames: List<TimeFrame>,
    @JsonIgnore override val blockTotal: BigInteger? = null,
    @JsonIgnore override val hourTotal: BigInteger? = null,
    @JsonIgnore override val dayTotal: BigInteger? = null,
    @JsonIgnore override val weekTotal: BigInteger? = null,
    @JsonIgnore override val monthTotal: BigInteger? = null,
    @JsonIgnore override val yearTotal: BigInteger? = null,
    val total: BigInteger,
) : TimeFrameDocument
