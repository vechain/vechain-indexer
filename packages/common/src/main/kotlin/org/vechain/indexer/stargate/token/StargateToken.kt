package org.vechain.indexer.stargate.token

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views
import org.vechain.indexer.validator.Status

@JsonView(Views.Public::class)
data class StargateToken(
    val tokenId: String,
    val level: TokenLevel,
    val owner: String,
    @JsonInclude(JsonInclude.Include.ALWAYS) val manager: String? = null,
    val delegationStatus: Status,
    @JsonInclude(JsonInclude.Include.ALWAYS) val validatorId: String? = null,
    val totalRewardsClaimed: BigInteger,
    val totalBootstrapRewardsClaimed: BigInteger,
    val vetStaked: BigInteger,
    val migrated: Boolean,
    val boosted: Boolean,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore val delegationNextPeriod: Long? = null,
    @JsonIgnore val delegationPeriodLength: Long? = null,
    @JsonIgnore val validatorExiting: Boolean? = null,
) : IndexedDocument
