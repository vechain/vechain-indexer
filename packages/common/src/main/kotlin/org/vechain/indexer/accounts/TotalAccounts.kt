package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@Document(collection = IndexerNames.TOTAL_ACCOUNTS.COLLECTION)
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TotalAccounts(
    @JsonIgnore @Id val id: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val total: Long? = null,
    val timeFrame: TimeFrame? = null,
    val dayOfMonth: Long? = null, // 25
    val weekOfYear: Long? = null, // 43
    val month: Long? = null, // 10 (October)
    val year: Long? = null, // 2025
    @JsonIgnore val dayTotal: Long? = null,
    @JsonIgnore val weekTotal: Long? = null,
    @JsonIgnore val monthTotal: Long? = null,
    @JsonIgnore val yearTotal: Long? = null,
    @JsonIgnore @field:JsonView(Views.Internal::class) override val version: Int,
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
