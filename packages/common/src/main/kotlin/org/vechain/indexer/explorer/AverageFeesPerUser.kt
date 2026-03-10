package org.vechain.indexer.explorer

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.AVERAGE_FEES_PER_USER.COLLECTION)
data class AverageFeesPerUser(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val date: String,
    val dayStartTimestamp: Long,
    @Field(targetType = FieldType.DECIMAL128) val totalFeesPaid: BigDecimal,
    val dailyActiveUsers: Long,
    @Field(targetType = FieldType.DECIMAL128) val averageFeesPerUser: BigDecimal,
    @JsonIgnore override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
