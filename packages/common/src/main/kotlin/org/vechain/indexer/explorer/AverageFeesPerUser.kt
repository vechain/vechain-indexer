package org.vechain.indexer.explorer

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.AVERAGE_FEES_PER_USER.COLLECTION)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AverageFeesPerUser(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore override val version: Int = 1,
    @JsonIgnore val recordType: AverageFeesPerUserRecordType = AverageFeesPerUserRecordType.SUMMARY,
    val date: String,
    val dayStartTimestamp: Long? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalFeesPaid: BigDecimal? = null,
    val dailyActiveUsers: Long? = null,
    @Field(targetType = FieldType.DECIMAL128) val averageFeesPerUser: BigDecimal? = null,
    @JsonIgnore val origin: String? = null,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
