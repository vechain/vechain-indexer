package org.vechain.indexer.validator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.archive.Archive
import org.vechain.indexer.stargate.TokenLevel
import org.vechain.indexer.thor.model.Views

@Document(collection = "delegations")
data class Delegation
@ConstructorBinding
constructor(
    @Id val id: String,
    @JsonIgnore @JsonView(Views.Internal::class) override val version: Int,
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

@Document("delegations_archives")
@JsonView(Views.Public::class)
data class DelegationArchive
@ConstructorBinding
constructor(@Id override val id: String, override val data: Delegation) : Archive<Delegation>
