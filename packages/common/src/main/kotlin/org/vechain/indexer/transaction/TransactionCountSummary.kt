package org.vechain.indexer.transaction

import com.fasterxml.jackson.annotation.JsonIgnore
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument

/** Running totals up to and including the newest indexed block, read off that block's row. */
data class TransactionCountSummary(
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val totalTransactions: BigInteger,
    val totalClauses: BigInteger,
    val totalRevertedTransactions: BigInteger,
    val totalRevertedClauses: BigInteger,
) : IndexedDocument
