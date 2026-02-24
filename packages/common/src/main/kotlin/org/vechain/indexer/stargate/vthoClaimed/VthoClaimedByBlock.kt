package org.vechain.indexer.stargate.vthoClaimed

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.timeFrame.TimeFrameDocument

@Document(collection = IndexerNames.VTHO_CLAIMED_BY_BLOCK.COLLECTION)
data class VthoClaimedByBlock
@ConstructorBinding
constructor(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val total: BigInteger,
    val legacyRewards: BigInteger,
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
) : TimeFrameDocument
