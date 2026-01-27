package org.vechain.indexer.stargate.tokenReward

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TokenReward(
    @JsonIgnore val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val tokenId: String,
    val cycle: Long,
    val validator: String,
    val rewards: BigInteger,
    @JsonIgnore val effectiveStake: BigInteger? = null,
    val rewardPeriod: RewardPeriod,
    val dayOfMonth: Long, // 25
    val weekOfYear: Long, // 43
    val month: Long, // 10 (October)
    val year: Long, // 2025
    @JsonIgnore val dayReward: BigInteger? = null,
    @JsonIgnore val weekReward: BigInteger? = null,
    @JsonIgnore val monthReward: BigInteger? = null,
    @JsonIgnore val yearReward: BigInteger? = null,
    @JsonIgnore val cycleReward: BigInteger? = null,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
