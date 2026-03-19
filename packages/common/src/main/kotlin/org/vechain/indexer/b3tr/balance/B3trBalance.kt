package org.vechain.indexer.b3tr.balance

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@Document(collection = IndexerNames.B3TR_BALANCE.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class B3trBalance(
    @Id val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
    @Field(targetType = FieldType.DECIMAL128) var vot3Balance: BigInteger,
    @Field(targetType = FieldType.DECIMAL128) var b3trBalance: BigInteger,
    @Field(targetType = FieldType.DECIMAL128) var totalBalance: BigInteger,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}
