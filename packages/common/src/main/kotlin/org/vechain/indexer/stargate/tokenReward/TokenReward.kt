package org.vechain.indexer.stargate.tokenReward

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import java.time.LocalDate
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Views

@Document(collection = "stargate_token_rewards")
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TokenReward(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val tokenId: String,
    val cycle: Long,
    val validator: String,
    val rewards: BigInteger,
    val effectiveStake: BigInteger? = null,
    val rewardPeriod: RewardPeriod,
    val date: LocalDate, // full date (e.g., 2025-10-25)
    val dayOfMonth: Long, // 25
    val weekOfYear: Long, // 43
    val month: Long, // 10 (October)
    val year: Long, // 2025
    val dayReward: BigInteger? = null,
    val weekReward: BigInteger? = null,
    val monthReward: BigInteger? = null,
    val yearReward: BigInteger? = null,
    val cycleReward: BigInteger? = null,
    @JsonIgnore @JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}

@Document("stargate_token_rewards_archives")
@JsonView(Views.Public::class)
data class TokenRewardArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: TokenReward) : Archive<TokenReward>
