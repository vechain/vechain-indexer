package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.BigInteger

/** The fees one navigator earned in one round; `claimedAmount` is null until they are claimed. */
data class NavigatorFee(
    val navigator: String,
    val roundId: Int,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    @JsonIgnore val blockTimestamp: Long,
    @JsonIgnore val totalDeposited: BigDecimal,
    @JsonIgnore val claimedAmount: BigDecimal?,
    val claimedAt: Long?,
    val depositedAt: Long,
    val unlockRound: Long,
) {
    val id: String
        get() = buildId(navigator, roundId)

    val claimed: Boolean
        get() = claimedAmount != null

    @get:JsonProperty("totalDeposited")
    val totalDepositedValue: BigInteger
        get() = totalDeposited.toBigInteger()

    companion object {
        const val FEE_LOCK_PERIOD = 4L

        fun buildId(navigator: String, roundId: Int): String = "${navigator}_${roundId}"
    }
}
