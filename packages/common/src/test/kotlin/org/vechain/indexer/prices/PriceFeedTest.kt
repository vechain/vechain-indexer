package org.vechain.indexer.prices

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

internal class PriceFeedTest {

    @Test
    fun `toBytes32 right-pads ASCII feed key to 32 bytes`() {
        val expectedAsciiPrefix =
            mapOf(
                PriceFeed.VET_USD to "vet-usd",
                PriceFeed.VTHO_USD to "vtho-usd",
                PriceFeed.B3TR_USD to "b3tr-usd",
                PriceFeed.EURT_USD to "eurt-usd",
                PriceFeed.GBP_USD to "gbp-usd",
                PriceFeed.BRL_USD to "brl-usd",
            )

        expectedAsciiPrefix.forEach { (feed, ascii) ->
            val bytes = feed.toBytes32()
            expectThat(bytes.size).isEqualTo(32)

            val prefix = ascii.toByteArray(Charsets.US_ASCII)
            expectThat(bytes.copyOfRange(0, prefix.size).toList()).isEqualTo(prefix.toList())
            expectThat(bytes.drop(prefix.size).all { it == 0.toByte() }).isEqualTo(true)
        }
    }

    @Test
    fun `toBytes32 returns a defensive copy`() {
        val first = PriceFeed.VET_USD.toBytes32()
        first[0] = 0xFF.toByte()
        val second = PriceFeed.VET_USD.toBytes32()
        expectThat(second[0]).isEqualTo(0x76.toByte()) // 'v'
    }

    @Test
    fun `enum exposes the canonical six mainnet feeds`() {
        expectThat(PriceFeed.entries.map { it.name })
            .hasSize(6)
            .isEqualTo(listOf("VET_USD", "VTHO_USD", "B3TR_USD", "EURT_USD", "GBP_USD", "BRL_USD"))
    }
}
