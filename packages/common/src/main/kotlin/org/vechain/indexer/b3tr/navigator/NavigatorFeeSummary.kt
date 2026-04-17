package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.NAVIGATOR_FEE_SUMMARY.COLLECTION)
data class NavigatorFeeSummaryDocument(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore val recordType: NavigatorFeeSummaryRecordType,
    @JsonIgnore val navigator: String? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalEarned: BigDecimal,
    @Field(targetType = FieldType.DECIMAL128) val totalClaimed: BigDecimal,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        const val GLOBAL_ID = "global"

        fun navigatorSummaryId(address: String): String = "navigator:$address"
    }
}

enum class NavigatorFeeSummaryRecordType {
    GLOBAL_SUMMARY,
    NAVIGATOR_SUMMARY,
}
