package org.vechain.indexer.stargate.vetDelegated

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument
import org.vechain.indexer.stargate.token.LevelledValue
import org.vechain.indexer.stargate.token.TokenLevel

data class VetDelegatedByBlock(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val total: BigInteger,
    override val byLevel: Map<TokenLevel, BigInteger>,
    val totalNftCount: Long = 0,
    val nftCountByLevel: Map<TokenLevel, Long> = emptyMap(),
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
) : TimeFrameDocument, LevelledValue<BigInteger> {
    override fun valueForLevel(level: TokenLevel?): BigInteger =
        if (level == null) total else byLevel[level] ?: BigInteger.ZERO

    fun nftCountForLevel(level: TokenLevel?): Long =
        if (level == null) totalNftCount else nftCountByLevel[level] ?: 0L
}
