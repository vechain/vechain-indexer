package org.vechain.indexer.b3tr.navigator

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.BigInteger

/**
 * A citizen's delegation to a navigator; `active` is false once removed or the navigator is gone.
 */
data class NavigatorCitizen(
    val address: String,
    @JsonIgnore val blockId: String,
    @JsonIgnore val blockNumber: Long,
    @JsonIgnore val blockTimestamp: Long,
    val navigator: String,
    @JsonIgnore val amount: BigDecimal,
    val delegatedAt: Long,
    val active: Boolean,
) {
    @get:JsonProperty("amount")
    val amountValue: BigInteger
        get() = amount.toBigInteger()
}
