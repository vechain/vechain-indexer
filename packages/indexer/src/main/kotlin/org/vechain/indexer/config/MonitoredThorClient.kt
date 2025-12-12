package org.vechain.indexer.config

import com.github.kittinunf.fuel.core.FuelError
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.RateLimitException
import org.vechain.indexer.metrics.Metrics
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.*

private const val TIP_POLL_MIN_DELAY_MS = 1_000L
private const val TIP_POLL_INITIAL_DELAY_MS = 4_000L
private const val TIP_POLL_DELAY_STEP_MS = 500L
private const val TIP_POLL_ERROR_DELAY_MS = 10_000L
private const val RATE_LIMIT_DELAY_MS = 30_000L

/** Metrics decorator for [ThorClient]. */
class MonitoredThorClient(private val thorClient: ThorClient) : ThorClient {

    private val logger = LoggerFactory.getLogger(MonitoredThorClient::class.java)

    private suspend fun <T> withMetrics(method: String, path: String, block: suspend () -> T): T {
        val start = System.nanoTime()
        try {
            val result = block()
            Metrics.recordResponseCode(method, path, "200")
            return result
        } catch (fuelErr: FuelError) {
            val statusCode = fuelErr.response.statusCode
            Metrics.recordResponseCode(method, path, statusCode.toString())
            throw fuelErr
        } catch (ex: BlockNotFoundException) {
            Metrics.recordResponseCode(method, path, "200")
            throw ex
        } catch (ex: RateLimitException) {
            Metrics.recordResponseCode(method, path, "429")
            throw ex
        } catch (ex: Exception) {
            Metrics.recordResponseCode(method, path, "unknown-exception")
            throw ex
        } finally {
            val durationMs = (System.nanoTime() - start) / 1_000_000.0
            Metrics.observeRequestDuration(method, path, durationMs)
        }
    }

    override suspend fun getBlock(blockNumber: Long): Block =
        withMetrics("GET", "/blocks/{number}") { thorClient.getBlock(blockNumber) }

    override suspend fun waitForBlock(blockNumber: Long): Block {
        val startTime = System.currentTimeMillis()
        var delayMs = TIP_POLL_INITIAL_DELAY_MS
        var attempts = 0
        while (true) {
            attempts++
            try {
                val block = getBlock(blockNumber)
                val totalTime = System.currentTimeMillis() - startTime
                if (attempts > 1) {
                    logger.info(
                        "Block {} fetched after {} attempts, total wait: {}ms",
                        blockNumber,
                        attempts,
                        totalTime,
                    )
                }
                return block
            } catch (e: BlockNotFoundException) {
                logger.info(
                    "Block {} not yet available, waiting {}ms (attempt {})",
                    blockNumber,
                    delayMs,
                    attempts,
                )
                delay(delayMs)
                delayMs = (delayMs - TIP_POLL_DELAY_STEP_MS).coerceAtLeast(TIP_POLL_MIN_DELAY_MS)
            } catch (e: RateLimitException) {
                logger.warn(
                    "Rate limited on block {}, backing off {}ms (attempt {})",
                    blockNumber,
                    RATE_LIMIT_DELAY_MS,
                    attempts,
                )
                delay(RATE_LIMIT_DELAY_MS)
            } catch (e: Exception) {
                logger.warn(
                    "Error fetching block {} (attempt {}), retrying in {}ms...",
                    blockNumber,
                    attempts,
                    TIP_POLL_ERROR_DELAY_MS,
                    e,
                )
                delay(TIP_POLL_ERROR_DELAY_MS)
            }
        }
    }

    override suspend fun getBestBlock(): Block =
        withMetrics("GET", "/blocks/best") { thorClient.getBestBlock() }

    override suspend fun getFinalizedBlock(): Block =
        withMetrics("GET", "/blocks/finalized") { thorClient.getFinalizedBlock() }

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> =
        withMetrics("POST", "/logs/event") { thorClient.getEventLogs(req) }

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> =
        withMetrics("POST", "/logs/transfer") { thorClient.getVetTransfers(req) }

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        blockID: String,
    ): List<InspectionResult> =
        withMetrics("POST", "/accounts/*") { thorClient.inspectClauses(clauses, blockID) }
}
