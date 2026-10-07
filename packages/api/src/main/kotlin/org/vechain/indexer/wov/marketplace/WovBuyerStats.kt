package org.vechain.indexer.wov.marketplace

import io.swagger.v3.oas.annotations.media.Schema
import org.vechain.indexer.thor.Address

data class WovBuyerStats(
    @Schema(description = "The buyer's address.") val buyer: String,
    @Schema(description = "Items bought in the window, over every payment token.")
    val itemCount: Long,
    val spend: List<WovSpend>,
) {
    companion object {
        fun from(window: WovBuyerWindow): WovBuyerStats =
            WovBuyerStats(
                buyer = window.buyer,
                itemCount = window.items,
                spend =
                    window.spend.map {
                        WovSpend(
                            it.paymentToken,
                            SYMBOLS[it.paymentToken],
                            it.items,
                            it.spend.toString(),
                        )
                    },
            )

        private val SYMBOLS =
            mapOf(
                Address.ZERO_ADDRESS to "VET",
                "0x170f4ba8e7acf6510f55db26047c83d13498af8a" to "WoV",
                "0x45429a2255e7248e57fce99e7239aed3f84b7a53" to "VVET",
            )
    }
}

data class WovSpend(
    @Schema(description = "The payment token's contract; the zero address is native VET.")
    val token: String,
    @Schema(description = "VET, WoV or VVET; null for any other token.", nullable = true)
    val symbol: String?,
    @Schema(description = "Items bought with this token in the window.") val items: Long,
    @Schema(description = "Spend in the token's smallest unit (wei), as a decimal string.")
    val amount: String,
)
