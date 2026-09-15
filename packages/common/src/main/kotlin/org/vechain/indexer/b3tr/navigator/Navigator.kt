package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.BigInteger

/** A navigator's state; the rounds and the exit deadline stay decimal strings on the wire. */
data class Navigator(
    val address: String,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    @JsonIgnore val blockTimestamp: Long,
    val status: NavigatorStatus,
    @JsonIgnore val stake: BigDecimal,
    val citizenCount: Int,
    @JsonIgnore val totalDelegated: BigDecimal,
    val metadataURI: String?,
    val registeredAt: Long,
    @JsonIgnore val exitAnnouncedRound: Long?,
    @JsonIgnore val exitEffectiveDeadlineBlock: Long?,
    @JsonIgnore val lastReportRound: Long?,
    val lastReportURI: String?,
) {
    @get:JsonProperty("stake")
    val stakeValue: BigInteger
        get() = stake.toBigInteger()

    @get:JsonProperty("totalDelegated")
    val totalDelegatedValue: BigInteger
        get() = totalDelegated.toBigInteger()

    @get:JsonProperty("exitAnnouncedRound")
    val exitAnnouncedRoundValue: String?
        get() = exitAnnouncedRound?.toString()

    val exitEffectiveDeadline: String?
        get() = exitEffectiveDeadlineBlock?.toString()

    @get:JsonProperty("lastReportRound")
    val lastReportRoundValue: String?
        get() = lastReportRound?.toString()
}

enum class NavigatorStatus {
    ACTIVE,
    EXITING,
    DEACTIVATED,
}
