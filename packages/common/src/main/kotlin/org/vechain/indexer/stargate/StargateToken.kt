package org.vechain.indexer.stargate

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
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonView(Views.Public::class)
data class StargateToken(
    @Id val tokenId: String,
    val level: TokenLevel,
    val owner: String,
    val manager: String? = null,
    val delegationStatus: Status,
    val validatorId: String? = null,
    val totalRewardsClaimed: BigInteger,
    val totalBootstrapRewardsClaimed: BigInteger,
    val vetStaked: BigInteger,
    val migrated: Boolean,
    val boosted: Boolean,
    override val blockNumber: Long,
    override val blockId: String,
    override val blockTimestamp: Long,
    @JsonView(Views.Internal::class) override val version: Int,
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
