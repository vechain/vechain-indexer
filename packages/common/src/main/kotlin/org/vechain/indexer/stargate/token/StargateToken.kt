package org.vechain.indexer.stargate.token

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.thor.model.Views
import org.vechain.indexer.validator.Status

@Document("stargate_tokens")
@JsonView(Views.Public::class)
data class StargateToken(
    @Id val tokenId: String,
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
    @JsonIgnore @JsonView(Views.Internal::class) override val version: Int,
    @JsonIgnore val delegationNextPeriod: Long? = null,
    @JsonIgnore val delegationPeriodLength: Long? = null,
    @JsonIgnore val validatorExiting: Boolean? = null,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = tokenId
}

@Document("stargate_tokens_archives")
@JsonView(Views.Public::class)
data class StargateTokenArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: StargateToken) : Archive<StargateToken>
