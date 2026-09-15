package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views

/** The accounts seen up to [blockNumber]; one row per block the count moved or a period closed. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountTotalsSeries(
    override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val totalAccounts: Long,
    /** The boundaries this row is the first past, which the API samples a range by. */
    @JsonIgnore val timeFrames: List<TimeFrame> = emptyList(),
) : IndexedDocument
