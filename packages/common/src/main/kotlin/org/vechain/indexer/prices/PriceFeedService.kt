package org.vechain.indexer.prices

import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.exception.PriceFeedUnavailableException
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.NumberUtils

/**
 * USD prices for assets quoted by the vechain.energy `PriceFeedOracle` contract.
 *
 * Each public method is `@Cacheable("price_feed_value")` so external callers share entries scoped
 * to that method's arguments. TTL is configured via `cache.caches.price_feed_value.ttl-seconds`.
 * The first request after expiry pays the oracle round-trip; with a 30s TTL that's bounded to a
 * handful of upstream calls per minute per process.
 *
 * Every public method throws [PriceFeedUnavailableException] on any read failure so the surrounding
 * endpoint can fail explicitly (mapped to HTTP 503 by `ExceptionResponseConfig`) instead of
 * silently returning a half-populated response.
 *
 * The injected [ThorClient] is the `priceOracleThorClient` configured by [PriceOracleConfig]; its
 * base URL and the contract address are wired per-deployment in terraform so the service always
 * reads from the right oracle for the active network.
 */
@Profile("prices")
@Component
open class PriceFeedService(
    @Qualifier("priceOracleThorClient") private val thorClient: ThorClient,
    @param:Value("\${pricing.oracle.contract-address}") private val priceFeedOracleAddress: String,
) {
    /**
     * Returns every published price feed in one batched call. Throws
     * [PriceFeedUnavailableException] on any oracle read failure.
     */
    @Cacheable(value = [PRICE_FEED_VALUE_CACHE], key = "'all'", sync = true)
    open fun getAllPrices(): Map<PriceFeed, BigDecimal> = fetchFromOracle(PriceFeed.entries.toSet())

    /**
     * Returns the latest USD price for [feed]. Throws [PriceFeedUnavailableException] if the oracle
     * isn't configured or the read failed.
     */
    @Cacheable(value = [PRICE_FEED_VALUE_CACHE], key = "#feed", sync = true)
    open fun getPrice(feed: PriceFeed): BigDecimal = fetchFromOracle(setOf(feed)).getValue(feed)

    /**
     * Returns the latest USD price for every feed in [feeds]. Throws
     * [PriceFeedUnavailableException] if the read failed.
     */
    @Cacheable(value = [PRICE_FEED_VALUE_CACHE], key = "#feeds", sync = true)
    open fun getPrices(feeds: Set<PriceFeed>): Map<PriceFeed, BigDecimal> {
        require(feeds.isNotEmpty()) { "feeds must not be empty" }
        return fetchFromOracle(feeds)
    }

    private fun fetchFromOracle(feeds: Set<PriceFeed>): Map<PriceFeed, BigDecimal> {
        if (priceFeedOracleAddress.isBlank()) {
            throw PriceFeedUnavailableException(
                "PriceFeedOracle contract address is not configured"
            )
        }
        val ordered = feeds.toList()
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
        if (responses.size != clauses.size) {
            throw PriceFeedUnavailableException(
                "PriceFeedOracle returned ${responses.size} responses for ${clauses.size} clauses (counts differ)"
            )
        }
        return ordered
            .mapIndexed { index, feed -> feed to decodeValue(feed, responses[index]) }
            .toMap()
    }

    private fun decodeValue(feed: PriceFeed, result: InspectionResult): BigDecimal {
        if (result.reverted || result.vmError != null) {
            throw PriceFeedUnavailableException(
                "PriceFeedOracle call reverted for $feed: ${result.vmError ?: "no reason"}"
            )
        }
        val data = result.data
        if (data.isBlank() || data == "0x") {
            throw PriceFeedUnavailableException("PriceFeedOracle returned empty data for $feed")
        }
        val decoded =
            try {
                FunctionReturnDecoder.decode(data, OUTPUTS)
            } catch (e: Exception) {
                throw PriceFeedUnavailableException(
                    "PriceFeedOracle response for $feed could not be decoded: ${e.message}",
                    e,
                )
            }
        val raw =
            decoded["value"] as? BigInteger
                ?: throw PriceFeedUnavailableException(
                    "PriceFeedOracle response for $feed missing 'value' field"
                )
        // Oracle returns uint128 scaled by 10^12; convert to a USD BigDecimal.
        return NumberUtils.toUSD(raw)
    }

    companion object {
        const val PRICE_FEED_VALUE_CACHE = "price_feed_value"

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
