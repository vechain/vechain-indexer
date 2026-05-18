package org.vechain.indexer.prices

import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.exception.PriceFeedUnavailableException
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

internal class PriceFeedServiceTest {

    private val thorClient: ThorClient = mockk()
    private val contractAddress = "0x49eC7192BF804Abc289645ca86F1eD01a6C17713"

    private val service =
        PriceFeedService(thorClient = thorClient, priceFeedOracleAddress = contractAddress)

    @Test
    fun `getPrice decodes the oracle uint128 value scaled by 10^12`() {
        // $1.50 USD scaled by 10^12 = 1_500_000_000_000
        val scaled = BigInteger("1500000000000")
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns listOf(inspectionResult(scaled))

        val price = service.getPrice(PriceFeed.VET_USD)

        expectThat(price).isEqualTo(BigDecimal("1.500000000000"))
    }

    @Test
    fun `getPrices returns a map keyed by feed`() {
        val vet = BigInteger("1500000000000") // $1.50
        val vtho = BigInteger("2000000000") // $0.002
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns listOf(inspectionResult(vet), inspectionResult(vtho))

        val prices = service.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))

        expectThat(prices).hasSize(2)
        expectThat(prices.getValue(PriceFeed.VET_USD)).isEqualTo(BigDecimal("1.500000000000"))
        expectThat(prices.getValue(PriceFeed.VTHO_USD)).isEqualTo(BigDecimal("0.002000000000"))
    }

    @Test
    fun `getAllPrices fetches every published feed in a single batch`() {
        val scaled = BigInteger("1000000000000") // $1.00
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns PriceFeed.entries.map { inspectionResult(scaled) }

        val prices = service.getAllPrices()

        expectThat(prices).hasSize(PriceFeed.entries.size)
        expectThat(prices.keys.toList()).isEqualTo(PriceFeed.entries.toList())
    }

    @Test
    fun `builds one clause per feed with the getLatestValue selector and bytes32 id`() {
        val captured = slot<List<Clause>>()
        coEvery { thorClient.inspectClauses(capture(captured), BlockRevision.Keyword.BEST) } returns
            listOf(
                inspectionResult(BigInteger.ONE),
                inspectionResult(BigInteger.ONE),
                inspectionResult(BigInteger.ONE),
            )

        val requested = listOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD, PriceFeed.B3TR_USD)
        service.getPrices(requested.toSet())

        val expectedSelector = ContractUtils.getFunctionSignature("getLatestValue(bytes32)")
        val clauses = captured.captured
        expectThat(clauses).hasSize(requested.size)

        clauses.zip(requested).forEach { (clause, feed) ->
            expectThat(clause.to).isEqualTo(contractAddress)
            // calldata layout: "0x" + 4-byte selector (8 hex) + 32-byte arg (64 hex)
            val raw = clause.data.removePrefix("0x").lowercase()
            expectThat(raw.length).isEqualTo(8 + 64)
            expectThat(raw.substring(0, 8)).isEqualTo(expectedSelector.lowercase())
            expectThat(raw.substring(8)).isEqualTo(feed.toBytes32().toHex())
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    @Test
    fun `throws PriceFeedUnavailableException when the contract address is blank`() {
        val unconfigured = PriceFeedService(thorClient = thorClient, priceFeedOracleAddress = "")

        val exception =
            assertThrows<PriceFeedUnavailableException> { unconfigured.getPrice(PriceFeed.VET_USD) }

        expectThat(exception.message)
            .isEqualTo("PriceFeedOracle contract address is not configured")
    }

    @Test
    fun `throws PriceFeedUnavailableException when inspectClauses raises`() {
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } throws IllegalStateException("connection refused")

        val exception =
            assertThrows<PriceFeedUnavailableException> { service.getPrice(PriceFeed.VET_USD) }

        expectThat(exception.message).isEqualTo("PriceFeedOracle read failed: connection refused")
    }

    @Test
    fun `throws PriceFeedUnavailableException when fewer responses than clauses`() {
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns emptyList()

        assertThrows<PriceFeedUnavailableException> {
            service.getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
        }
    }

    @Test
    fun `throws PriceFeedUnavailableException when response data is blank`() {
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns listOf(emptyInspectionResult(""))

        assertThrows<PriceFeedUnavailableException> { service.getPrice(PriceFeed.VET_USD) }
    }

    @Test
    fun `throws PriceFeedUnavailableException when response data is 0x`() {
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns listOf(emptyInspectionResult("0x"))

        assertThrows<PriceFeedUnavailableException> { service.getPrice(PriceFeed.VET_USD) }
    }

    @Test
    fun `throws PriceFeedUnavailableException when the clause reverted`() {
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns
            listOf(
                inspectionResult(BigInteger.ONE)
                    .copy(reverted = true, vmError = "execution reverted")
            )

        val exception =
            assertThrows<PriceFeedUnavailableException> { service.getPrice(PriceFeed.VET_USD) }

        expectThat(exception.message)
            .isEqualTo("PriceFeedOracle call reverted for VET_USD: execution reverted")
    }

    @Test
    fun `throws PriceFeedUnavailableException when the clause reports a vmError`() {
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns listOf(inspectionResult(BigInteger.ONE).copy(vmError = "out of gas"))

        assertThrows<PriceFeedUnavailableException> { service.getPrice(PriceFeed.VET_USD) }
    }

    @Test
    fun `wraps decoder exceptions as PriceFeedUnavailableException`() {
        // Truncated payload that the decoder cannot parse as `(uint128, uint128)`.
        coEvery {
            thorClient.inspectClauses(any<List<Clause>>(), BlockRevision.Keyword.BEST)
        } returns listOf(emptyInspectionResult("0xdeadbeef"))

        assertThrows<PriceFeedUnavailableException> { service.getPrice(PriceFeed.VET_USD) }
    }

    private fun inspectionResult(
        value: BigInteger,
        updatedAt: BigInteger = BigInteger.valueOf(1_700_000_000L),
    ): InspectionResult = emptyInspectionResult(encodeUint128Pair(value, updatedAt))

    private fun emptyInspectionResult(data: String): InspectionResult =
        InspectionResult(
            data = data,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
            reverted = false,
            vmError = null,
        )

    /**
     * Builds the ABI return data for a `(uint128 value, uint128 updatedAt)` tuple: each value is
     * encoded as a 32-byte big-endian word, left-padded with zeros.
     */
    private fun encodeUint128Pair(value: BigInteger, updatedAt: BigInteger): String {
        val valueHex = value.toString(16).padStart(64, '0')
        val updatedHex = updatedAt.toString(16).padStart(64, '0')
        return "0x$valueHex$updatedHex"
    }
}
