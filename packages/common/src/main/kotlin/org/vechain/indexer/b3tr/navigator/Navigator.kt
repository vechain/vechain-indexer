package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.BigInteger
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.NAVIGATOR.COLLECTION)
data class Navigator
@ConstructorBinding
constructor(
    @Id val address: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val status: NavigatorStatus,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val stake: BigDecimal,
    val citizenCount: Int,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val totalDelegated: BigDecimal,
    val metadataURI: String?,
    val registeredAt: Long,
    val exitAnnouncedRound: String?,
    val exitEffectiveRound: String?,
    val lastReportRound: String?,
    val lastReportURI: String?,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address

    @get:JsonProperty("stake")
    val stakeValue: BigInteger
        get() = stake.toBigInteger()

    @get:JsonProperty("totalDelegated")
    val totalDelegatedValue: BigInteger
        get() = totalDelegated.toBigInteger()
}

enum class NavigatorStatus {
    ACTIVE,
    EXITING,
    DEACTIVATED,
}
