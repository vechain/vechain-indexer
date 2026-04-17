package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigDecimal
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.mapping.FieldType
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument

@Document(collection = IndexerNames.NAVIGATOR_OVERVIEW_SUMMARY.COLLECTION)
data class NavigatorOverviewSummary(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val version: Int,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    @JsonIgnore val recordType: NavigatorOverviewSummaryRecordType,
    @JsonIgnore val navigator: String? = null,
    @JsonIgnore val status: NavigatorStatus? = null,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val stake: BigDecimal? = null,
    @JsonIgnore val citizenCount: Int? = null,
    @JsonIgnore @Field(targetType = FieldType.DECIMAL128) val delegatedTotal: BigDecimal? = null,
    @JsonIgnore val exitEffectiveDeadlineBlock: Long? = null,
    val activeNavigators: Long? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalStaked: BigDecimal? = null,
    val totalCitizens: Long? = null,
    @Field(targetType = FieldType.DECIMAL128) val totalDelegated: BigDecimal? = null,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id

    companion object {
        const val GLOBAL_ID = "global"

        fun navigatorStateId(address: String): String = "navigator:$address"
    }
}

enum class NavigatorOverviewSummaryRecordType {
    GLOBAL_SUMMARY,
    NAVIGATOR_STATE,
}
