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
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.NAVIGATOR_DELEGATION_EVENT.COLLECTION)
data class NavigatorDelegationEvent
@ConstructorBinding
constructor(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    override val blockTimestamp: Long,
    val txId: String,
    val navigator: String,
    val citizen: String,
    val eventType: String,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val amount: BigDecimal?,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val delta: BigDecimal?,
) : IndexedDocument {

    @get:JsonProperty("amount")
    val amountValue: BigInteger?
        get() = amount?.toBigInteger()

    @get:JsonProperty("delta")
    val deltaValue: BigInteger?
        get() = delta?.toBigInteger()
}
