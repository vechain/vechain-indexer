package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.VersionedDocument
import org.vechain.indexer.thor.model.Views

@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class TotalAccounts(
    @JsonIgnore val id: String,
    @JsonIgnore override val version: Int,
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
) : VersionedDocument {
    @JsonIgnore override fun getDocumentId(): String = id
}
