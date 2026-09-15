package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import org.vechain.indexer.IndexedDocument

/** The accounts seen up to [blockNumber]; one row per block the count moved or a period closed. */
data class AccountTotalsSeries(
    @JsonIgnore override val blockId: String,
    override val blockNumber: Long,
    override val blockTimestamp: Long,
    val totalAccounts: Long,
    /** The boundaries this row is the first past, which the API samples a range by. */
    @JsonIgnore val timeFrames: List<TimeFrame> = emptyList(),
) : IndexedDocument
