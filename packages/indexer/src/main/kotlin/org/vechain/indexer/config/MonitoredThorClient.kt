package org.vechain.indexer.thor.client

import com.fasterxml.jackson.core.type.TypeReference
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.result.Result
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Histogram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.thor.model.*
import org.vechain.indexer.utils.JsonUtils

private const val TIP_POLL_MIN_DELAY_MS = 1_000L
private const val TIP_POLL_INITIAL_DELAY_MS = 4_000L
private const val TIP_POLL_DELAY_STEP_MS = 500L
private const val TIP_POLL_ERROR_DELAY_MS = 10_000L

private val requestDurationHistogram =
    Histogram.builder()
        .name("thor_client_request_duration")
        .help("Duration of Thor client requests in milliseconds")
        .labelNames("endpoint")
        .classicUpperBounds(0.01, 1.0, 5.0, 10.0, 50.0, 100.0, 250.0, 500.0, 1000.0) // milliseconds
        .register()

private val responseCodeCounter =
    Counter.builder()
        .name("thor_client_response_codes_total")
        .help("Count of response codes from Thor client")
        .labelNames("endpoint", "code")
        .register()

/**
 * Monitored implementation of the {@link org.vechain.indexer.thor.client.ThorClient.class
 * ThorClient} using the Fuel HTTP library and Jackson JSON mapper with Prometheus metrics.
 *
 * @see <a href="https://github.com/kittinunf/fuel">Fuel Library</a>
 */
class MonitoredThorClient(
    private val baseUrl: String,
    private vararg val headers: Pair<String, Any>,
) : ThorClient {

    private val logger = LoggerFactory.getLogger(MonitoredThorClient::class.java)
    private val objectMapper = JsonUtils.mapper

    private inline fun <T> withMetrics(endpoint: String, block: () -> T): T {
        val start = System.nanoTime()
        try {
            val result = block()
            recordResponseCode(endpoint, "200")
            return result
        } catch (fuelErr: FuelError) {
            val statusCode = fuelErr.response.statusCode
            recordResponseCode(endpoint, statusCode.toString())
            throw fuelErr
        } catch (ex: Exception) {
            recordResponseCode(endpoint, "unknown-exception")
            throw ex
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000.0
            requestDurationHistogram.labelValues(endpoint).observe(durationMs)
        }
    }

    private fun recordResponseCode(endpoint: String, code: String) {
        responseCodeCounter.labelValues(endpoint, code).inc()
    }

    override suspend fun getBlock(blockNumber: Long): Block =
        withContext(Dispatchers.IO) {
            withMetrics("getBlock") {
                val (_, response, result) =
                    Fuel.get("$baseUrl/blocks/$blockNumber?expanded=true")
                        .appendHeader(*headers)
                        .response()

                val responseBody =
                    when (result) {
                        is Result.Success -> result.get().toString(Charsets.UTF_8)
                        is Result.Failure -> {
                            recordResponseCode(
                                "getBlock",
                                result.error.response.statusCode.toString(),
                            )
                            throw result.error
                        }
                    }

                if (responseBody.isEmpty() || responseBody.trim() == "null") {
                    throw BlockNotFoundException("Block $blockNumber not found")
                }

                objectMapper.readValue(responseBody, Block::class.java)
            }
        }

    override suspend fun waitForBlock(blockNumber: Long): Block {
        var delayMs = TIP_POLL_INITIAL_DELAY_MS
        while (true) {
            try {
                return getBlock(blockNumber)
            } catch (e: BlockNotFoundException) {
                delay(delayMs)
                delayMs = (delayMs - TIP_POLL_DELAY_STEP_MS).coerceAtLeast(TIP_POLL_MIN_DELAY_MS)
            } catch (e: Exception) {
                logger.warn("Error fetching block $blockNumber, retrying...", e)
                delay(TIP_POLL_ERROR_DELAY_MS)
            }
        }
    }

    override suspend fun getBestBlock(): Block =
        withContext(Dispatchers.IO) {
            withMetrics("getBestBlock") {
                val (_, response, result) =
                    Fuel.get("$baseUrl/blocks/best?expanded=true").appendHeader(*headers).response()

                val responseBody =
                    when (result) {
                        is Result.Success -> result.get().toString(Charsets.UTF_8)
                        is Result.Failure -> throw result.error
                    }

                objectMapper.readValue(responseBody, Block::class.java)
            }
        }

    override suspend fun getFinalizedBlock(): Block =
        withContext(Dispatchers.IO) {
            withMetrics("getFinalizedBlock") {
                val (_, response, result) =
                    Fuel.get("$baseUrl/blocks/finalized?expanded=true")
                        .appendHeader(*headers)
                        .response()

                val responseBody =
                    when (result) {
                        is Result.Success -> result.get().toString(Charsets.UTF_8)
                        is Result.Failure -> throw result.error
                    }

                objectMapper.readValue(responseBody, Block::class.java)
            }
        }

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> =
        withContext(Dispatchers.IO) {
            withMetrics("getEventLogs") {
                val (_, response, result) =
                    Fuel.post("$baseUrl/logs/event")
                        .body(JsonUtils.mapper.writeValueAsBytes(req))
                        .appendHeader(*headers)
                        .response()

                val responseBody =
                    when (result) {
                        is Result.Success -> result.get().toString(Charsets.UTF_8)
                        is Result.Failure -> throw result.error
                    }

                objectMapper.readValue(responseBody, object : TypeReference<List<EventLog>>() {})
            }
        }

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> =
        withContext(Dispatchers.IO) {
            withMetrics("getVetTransfers") {
                val (_, response, result) =
                    Fuel.post("$baseUrl/logs/transfer")
                        .body(JsonUtils.mapper.writeValueAsBytes(req))
                        .appendHeader(*headers)
                        .response()

                val responseBody =
                    when (result) {
                        is Result.Success -> result.get().toString(Charsets.UTF_8)
                        is Result.Failure -> throw result.error
                    }

                objectMapper.readValue(responseBody, object : TypeReference<List<TransferLog>>() {})
            }
        }

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        blockID: String,
    ): List<InspectionResult> =
        withContext(Dispatchers.IO) {
            withMetrics("inspectClauses") {
                val req = InspectionRequest(clauses)
                val body = JsonUtils.mapper.writeValueAsBytes(req)
                val (_, response, result) =
                    Fuel.post("$baseUrl/accounts/*?revision=$blockID")
                        .body(body)
                        .appendHeader(*headers)
                        .response()

                val responseBody =
                    when (result) {
                        is Result.Success -> result.get().toString(Charsets.UTF_8)
                        is Result.Failure -> throw result.error
                    }

                objectMapper.readValue(
                    responseBody,
                    object : TypeReference<List<InspectionResult>>() {},
                )
            }
        }
}
