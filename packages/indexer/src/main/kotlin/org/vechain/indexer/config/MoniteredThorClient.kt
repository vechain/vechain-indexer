package org.vechain.indexer.thor.client

import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.result.Result
import io.prometheus.metrics.core.metrics.Counter
import io.prometheus.metrics.core.metrics.Histogram
import java.lang.Exception
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.thor.model.*
import org.vechain.indexer.utils.JsonUtils

private const val TIP_POLL_MIN_DELAY_MS = 1_000L
private const val TIP_POLL_INITIAL_DELAY_MS = 4_000L
private const val TIP_POLL_DELAY_STEP_MS = 500L
private const val TIP_POLL_ERROR_DELAY_MS = 10_000L

/**
 * Default implementation of the {@link org.vechain.indexer.thor.client.ThorClient.class ThorClient}
 * using the Fuel HTTP library and Jackson JSON mapper.
 *
 * @see <a href="https://github.com/kittinunf/fuel">Fuel Library</a>
 */
class MonitoredThorClient(
    private val baseUrl: String,
    private vararg val headers: Pair<String, Any>,
) : ThorClient {

    private val logger = LoggerFactory.getLogger(DefaultThorClient::class.java)
    private val objectMapper = JsonUtils.mapper

    private val requestDurationBucket =
        Histogram.builder()
            .name("thor_client_get_block_duration_seconds")
            .help("Duration of getBlock calls in seconds")
            .labelNames("endpoint")
            .register()

    private val responseCodeCounter =
        Counter.builder()
            .name("thor_client_response_codes_total")
            .help("Count of response codes from Thor client")
            .labelNames("endpoint", "code")
            .register()

    override suspend fun getBlock(blockNumber: Long): Block =
        withContext(Dispatchers.IO) {
            val start = System.nanoTime()

            val (_, _, result) =
                Fuel.get("$baseUrl/blocks/$blockNumber?expanded=true")
                    .appendHeader(*headers)
                    .response()

            val duration = System.nanoTime() - start
            val durationMS = duration.toDouble() / 1_000_000_000.0

            requestDurationBucket.labelValues("getBlock").observe(durationMS)

            val responseBody =
                when (result) {
                    is Result.Success -> {
                        responseCodeCounter.labelValues("getBlock", "200").inc()
                        result.get().toString(Charsets.UTF_8)
                    }
                    is Result.Failure -> {
                        responseCodeCounter
                            .labelValues("getBlock", result.error.response.statusCode.toString())
                            .inc()
                        throw Exception(
                            "Get block $blockNumber request failed with error: ${result.error}"
                        )
                    }
                }

            if (responseBody.isEmpty() || responseBody.trim() == "null") {
                throw BlockNotFoundException("Block $blockNumber not found")
            }

            return@withContext objectMapper.readValue(responseBody, Block::class.java)
        }

    override suspend fun waitForBlock(blockNumber: Long): Block {
        throw BlockNotFoundException("Block $blockNumber not found")
    }

    override suspend fun getBestBlock(): Block =
        withContext(Dispatchers.IO) {
            throw NotImplementedError("getBestBlock is not implemented in MonitoredThorClient")
        }

    override suspend fun getFinalizedBlock(): Block =
        withContext(Dispatchers.IO) {
            throw NotImplementedError("getFinalizedBlock is not implemented in MonitoredThorClient")
        }

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> =
        withContext(Dispatchers.IO) {
            throw NotImplementedError("getEventLogs is not implemented in MonitoredThorClient")
        }

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> =
        withContext(Dispatchers.IO) {
            throw NotImplementedError("getVetTransfers is not implemented in MonitoredThorClient")
        }

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        blockID: String,
    ): List<InspectionResult> {
        throw NotImplementedError("inspectClauses is not implemented in MonitoredThorClient")
    }
}
