package org.vechain.indexer.prices

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.exception.PriceFeedUnavailableException
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.NumberUtils

/**
 * USD prices for assets quoted by the vechain.energy `PriceFeedOracle` contract.
 *
 * Calls `getLatestValue(bytes32)` per feed in a single `inspectClauses` batch. The new [getPrice] /
 * [getPrices] API throws [PriceFeedUnavailableException] on any read failure so the surrounding
 * endpoint can fail explicitly (mapped to HTTP 503 by `ExceptionResponseConfig`) instead of
 * silently returning a half-populated response.
 *
 * The legacy [get] helper preserves the previous null-on-failure behaviour for the V1/V2 validator
 * endpoints; it will be removed once those callers are migrated.
 */
@Profile("prices")
@Component
open class PriceFeedService(
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.PRICE_FEED_ORACLE_CONTRACT:}")
    private val priceFeedOracleAddress: String,
) {
    /**
     * Snapshot of VET and VTHO USD prices.
     *
     * Retained as a back-compat shim for the V1/V2 validator endpoints. New callers should use
     * [getPrice] / [getPrices] and receive a [PriceFeedUnavailableException] on failure.
     */
    data class Prices(val vetUsd: BigDecimal, val vthoUsd: BigDecimal)

    private data class Cached(val prices: Prices?, val fetchedAt: Long)

    @Volatile private var cache: Cached? = null
    private val fetchLock = Any()
    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * Returns the cached VET/VTHO pair, fetching fresh values when the cache is stale or absent.
     * Returns `null` if the oracle isn't configured or the read failed — preserved for the existing
     * validator endpoints. Will be removed once those callers migrate to [getPrices].
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
            val fresh =
                try {
                    val map = getPrices(setOf(PriceFeed.VET_USD, PriceFeed.VTHO_USD))
                    Prices(
                        vetUsd = map.getValue(PriceFeed.VET_USD),
                        vthoUsd = map.getValue(PriceFeed.VTHO_USD),
                    )
                } catch (e: PriceFeedUnavailableException) {
                    logger.debug(
                        "PriceFeedOracle read failed; treating prices as unavailable: {}",
                        e.message,
                    )
                    null
                }
            cache = Cached(fresh, afterLock)
            return fresh
        }
    }

    /**
     * Returns the latest USD price for [feed]. Throws [PriceFeedUnavailableException] if the oracle
     * isn't configured or the read failed.
     */
    open fun getPrice(feed: PriceFeed): BigDecimal = getPrices(setOf(feed)).getValue(feed)

    /**
     * Returns the latest USD price for every feed in [feeds] in a single `inspectClauses` batch.
     * Throws [PriceFeedUnavailableException] if any clause is missing data or the call fails.
     *
     * The result is keyed by [PriceFeed]; iteration order matches the input collection.
     */
    open fun getPrices(feeds: Collection<PriceFeed>): Map<PriceFeed, BigDecimal> {
        require(feeds.isNotEmpty()) { "feeds must not be empty" }
        if (priceFeedOracleAddress.isBlank()) {
            throw PriceFeedUnavailableException(
                "PriceFeedOracle contract address is not configured"
            )
        }
        val ordered = feeds.toSet().toList()
        val clauses =
            ordered.map { feed ->
                ContractUtils.createClause(
                    priceFeedOracleAddress,
                    GET_LATEST_VALUE_ABI,
                    feed.toBytes32(),
                )
            }
        val responses =
            try {
                runBlocking { thorClient.inspectClauses(clauses, BlockRevision.Keyword.BEST) }
            } catch (e: Exception) {
                throw PriceFeedUnavailableException("PriceFeedOracle read failed: ${e.message}", e)
            }
        if (responses.size < clauses.size) {
            throw PriceFeedUnavailableException(
                "PriceFeedOracle returned ${responses.size} responses for ${clauses.size} clauses"
            )
        }
        return ordered
            .mapIndexed { index, feed -> feed to decodeValue(feed, responses[index].data) }
            .toMap()
    }

    private fun decodeValue(feed: PriceFeed, data: String): BigDecimal {
        if (data.isBlank() || data == "0x") {
            throw PriceFeedUnavailableException("PriceFeedOracle returned empty data for $feed")
        }
        val decoded = FunctionReturnDecoder.decode(data, OUTPUTS)
        val raw =
            decoded["value"] as? BigInteger
                ?: throw PriceFeedUnavailableException(
                    "PriceFeedOracle response for $feed missing 'value' field"
                )
        // Oracle returns uint128 scaled by 10^12; convert to a USD BigDecimal.
        return NumberUtils.toUSD(raw)
    }

    companion object {
        private const val TTL_MILLIS = 30_000L

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
