package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.IndexerNames

@Document(collection = IndexerNames.ACCOUNT_TOTALS_SERIES.COLLECTION)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountTotalsSeries(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    @JsonIgnore
    val recordType: AccountTotalsSeriesRecordType = AccountTotalsSeriesRecordType.SERIES,
    val totalAccounts: Long? = null,
    @JsonIgnore val address: String? = null,
    @JsonIgnore val isHourly: Boolean?,
    @JsonIgnore val isDaily: Boolean?,
    @JsonIgnore val isWeekly: Boolean?,
    @JsonIgnore val isMonthly: Boolean?,
    @JsonIgnore @Id val id: String,
) : IndexedDocument
