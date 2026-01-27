package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
data class Delegation(
    @JsonIgnore val id: String,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    val validator: String,
    val tokenId: String,
    val owner: String,
    val status: Status,
    val tokenLevel: TokenLevel,
    val stakedAmount: String,
    val totalRewardsClaimed: BigInteger,
    @JsonIgnore val notify: Boolean = false,
    @JsonIgnore val txId: String,
    @JsonIgnore val validatorNextCycle: Long,
    @JsonIgnore val validatorCycleLength: Long,
    @JsonIgnore val force: Boolean = false,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
