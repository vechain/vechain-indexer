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

@Document(collection = IndexerNames.NAVIGATOR_FEE.COLLECTION)
data class NavigatorFee
@ConstructorBinding
constructor(
    @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val navigator: String,
    val roundId: Int,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val totalDeposited: BigDecimal,
    val claimed: Boolean,
    val claimedAt: Long?,
    val depositedAt: Long,
    val unlockRound: Long,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    @get:JsonProperty("totalDeposited")
    val totalDepositedValue: BigInteger
        get() = totalDeposited.toBigInteger()

    companion object {
        const val FEE_LOCK_PERIOD = 4L

        fun buildId(navigator: String, roundId: Int): String = "${navigator}_${roundId}"
    }
}
