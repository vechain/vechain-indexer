package org.vechain.indexer.stargate.vetDelegated

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument
import org.vechain.indexer.stargate.token.LevelledValue
import org.vechain.indexer.stargate.token.TokenLevel

@Document(collection = "stargate_total_vet_delegated_by_block")
data class VetDelegatedByBlock
@ConstructorBinding
constructor(
    @JsonIgnore override val blockId: String,
    @JsonIgnore @Id override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    override val total: BigInteger,
    override val byLevel: Map<TokenLevel, BigInteger>,
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
) : TimeFrameDocument, LevelledValue<BigInteger> {
    override fun valueForLevel(level: TokenLevel?): BigInteger =
        if (level == null) total else byLevel[level] ?: BigInteger.ZERO
}
