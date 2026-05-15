package org.vechain.indexer.validators

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.NumberUtils

/**
 * VET / VTHO USD prices for API-layer derivations (TVL, yields, NFT yields).
 *
 * Calls `PriceFeedOracle.getLatestValue(bytes32)` directly — no aggregator, no per-network
 * deployment assumptions. Results are cached for [TTL_MILLIS] so a single page-render hits the
 * chain at most once.
 *
 * Returns `null` if [priceFeedOracleAddress] isn't configured (solo / custom networks). Callers use
 * that signal to omit price-dependent fields from the response.
 */
@Component
open class PriceProvider(
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.PRICE_FEED_ORACLE_CONTRACT:}")
    private val priceFeedOracleAddress: String,
) {
    /**
     * Most-recently-fetched VET and VTHO USD prices, scaled to BigDecimal (10^-12 oracle units).
     */
    data class Prices(val vetUsd: BigDecimal, val vthoUsd: BigDecimal)

    private data class Cached(val prices: Prices?, val fetchedAt: Long)

    @Volatile private var cache: Cached? = null
    private val fetchLock = Any()
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the cached pair, fetching fresh values when the cache is stale or absent. Returns
     * `null` if the oracle isn't configured or the read failed.
     */
    open fun get(): Prices? {
        val now = System.currentTimeMillis()
        val current = cache
        if (current != null && now - current.fetchedAt < TTL_MILLIS) {
            return current.prices
        }
        synchronized(fetchLock) {
            val recheck = cache
            val afterLock = System.currentTimeMillis()
            if (recheck != null && afterLock - recheck.fetchedAt < TTL_MILLIS) {
                return recheck.prices
            }
            val fresh = fetch()
            cache = Cached(fresh, afterLock)
            return fresh
        }
    }

    private fun fetch(): Prices? {
        if (priceFeedOracleAddress.isBlank()) return null
        return try {
            val clauses =
                listOf(
                    ContractUtils.createClause(
                        priceFeedOracleAddress,
                        GET_LATEST_VALUE_ABI,
                        BigInteger(VET_ID.removePrefix("0x"), 16),
                    ),
                    ContractUtils.createClause(
                        priceFeedOracleAddress,
                        GET_LATEST_VALUE_ABI,
                        BigInteger(VTHO_ID.removePrefix("0x"), 16),
                    ),
                )
            val responses = runBlocking {
                thorClient.inspectClauses(clauses, BlockRevision.Keyword.BEST)
            }
            if (responses.size < 2) return null
            val vet = decodeValue(responses[0].data) ?: return null
            val vtho = decodeValue(responses[1].data) ?: return null
            Prices(vetUsd = vet, vthoUsd = vtho)
        } catch (e: Exception) {
            logger.debug(
                "PriceFeedOracle read failed; treating prices as unavailable: {}",
                e.message,
            )
            null
        }
    }

    private fun decodeValue(data: String): BigDecimal? {
        if (data.isBlank() || data == "0x") return null
        val decoded = FunctionReturnDecoder.decode(data, OUTPUTS)
        val raw = decoded["value"] as? BigInteger ?: return null
        // Oracle returns uint128 scaled by 10^12; convert to a USD BigDecimal.
        return NumberUtils.toUSD(raw)
    }

    companion object {
        private const val TTL_MILLIS = 30_000L

        // VeChain's PriceFeedOracle keys (bytes32 ASCII).
        private const val VET_ID =
            "0x7665742d75736400000000000000000000000000000000000000000000000000"
        private const val VTHO_ID =
            "0x7674686f2d757364000000000000000000000000000000000000000000000000"

        private val GET_LATEST_VALUE_ABI =
            FunctionDefinition(
                name = "getLatestValue",
                inputs = listOf(FunctionParameter("id", "bytes32")),
                outputs =
                    listOf(
                        FunctionParameter("value", "uint128"),
                        FunctionParameter("updatedAt", "uint128"),
                    ),
                stateMutability = "view",
            )

        private val OUTPUTS =
            listOf(
                InputOutput("uint128", "value", "uint128"),
                InputOutput("uint128", "updatedAt", "uint128"),
            )
    }
}
