package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.BigInteger

/** One delegation change: `amount` is the delegation after it, `delta` what the event moved. */
data class NavigatorDelegationEvent(
    @JsonIgnore val id: String,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    val blockTimestamp: Long,
    val txId: String,
    val navigator: String,
    val citizen: String,
    val eventType: String,
    @JsonIgnore val amount: BigDecimal,
    @JsonIgnore val delta: BigDecimal,
) {
    @get:JsonProperty("amount")
    val amountValue: BigInteger
        get() = amount.toBigInteger()

    @get:JsonProperty("delta")
    val deltaValue: BigInteger
        get() = delta.toBigInteger()
}
