package org.vechain.indexer.accounts

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonView
import java.math.BigInteger
import org.vechain.indexer.IndexedDocument
import org.vechain.indexer.thor.model.Views

/** An address's running totals as of [blockNumber]; the current row is the one not superseded. */
@JsonView(Views.Public::class)
@JsonInclude(JsonInclude.Include.NON_NULL)
data class AccountOverview(
    val address: String,
    @JsonIgnore override val blockId: String,
    @JsonIgnore override val blockNumber: Long,
    @JsonIgnore override val blockTimestamp: Long,
    val firstSeen: Long,
    var lastSeen: Long,
    var transactionsSent: Long = 0L,
    var clausesSent: Long = 0L,
    var vthoBurned: BigInteger = BigInteger.ZERO,
    var vthoDelegated: BigInteger = BigInteger.ZERO,
    var gasUsed: BigInteger = BigInteger.ZERO,
    var vetSent: BigInteger = BigInteger.ZERO,
    var vetReceived: BigInteger = BigInteger.ZERO,
    var vetBalance: BigInteger = BigInteger.ZERO,
    var vthoBlockRewards: BigInteger = BigInteger.ZERO,
    var vthoPassiveGeneration: BigInteger = BigInteger.ZERO,
    /** When passive VTHO was last credited, which the time-based generation counts from. */
    @JsonIgnore var lastVthoSettlement: Long? = null,
) : IndexedDocument
