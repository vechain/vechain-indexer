package org.vechain.indexer.stargate.vetDelegated

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument
import org.vechain.indexer.stargate.token.LevelledValue
import org.vechain.indexer.stargate.token.TokenLevel

@Document(collection = IndexerNames.VET_DELEGATED_BY_BLOCK.COLLECTION)
data class VetDelegatedByBlock
@ConstructorBinding
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val total: BigInteger,
    override val byLevel: Map<TokenLevel, BigInteger>,
    val totalNftCount: Long = 0,
    val nftCountByLevel: Map<TokenLevel, Long> = emptyMap(),
    override val hourOfDay: Long,
    override val dayOfMonth: Long, // 25
    override val weekOfYear: Long, // 43
    override val month: Long, // 10 (October)
    override val year: Long, // 2025
    override val timeFrames: List<TimeFrame>,
    @JsonIgnore override val blockTotal: BigInteger? = null,
    @JsonIgnore override val hourTotal: BigInteger? = null,
    @JsonIgnore override val dayTotal: BigInteger? = null,
    @JsonIgnore override val weekTotal: BigInteger? = null,
    @JsonIgnore override val monthTotal: BigInteger? = null,
    @JsonIgnore override val yearTotal: BigInteger? = null,
    @Id val id: String = blockNumber.toString(),
) : TimeFrameDocument, LevelledValue<BigInteger> {
    override fun valueForLevel(level: TokenLevel?): BigInteger =
        if (level == null) total else byLevel[level] ?: BigInteger.ZERO

    fun nftCountForLevel(level: TokenLevel?): Long =
        if (level == null) totalNftCount else nftCountByLevel[level] ?: 0L
}
