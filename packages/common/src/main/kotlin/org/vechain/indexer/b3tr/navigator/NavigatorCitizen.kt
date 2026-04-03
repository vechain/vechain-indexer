package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.boot.context.properties.bind.ConstructorBinding
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.NAVIGATOR_CITIZEN.COLLECTION)
data class NavigatorCitizen
@ConstructorBinding
constructor(
    @Id val address: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val navigator: String,
    @Field(targetType = FieldType.DECIMAL128) val amount: BigDecimal,
    val delegatedAt: Long,
    val active: Boolean,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = address
}
