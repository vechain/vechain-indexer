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
    // Read from delegation.state by the API join; the indexer never sets them.
    val delegationStatus: Status = Status.NONE,
    @JsonInclude(JsonInclude.Include.ALWAYS) val validatorId: String? = null,
    val totalRewardsClaimed: BigInteger,
    val totalBootstrapRewardsClaimed: BigInteger,
    val vetStaked: BigInteger,
    val migrated: Boolean,
    val boosted: Boolean,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockTimestamp: Long,
) : IndexedDocument
