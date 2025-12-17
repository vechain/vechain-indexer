package org.vechain.indexer.config

import com.github.kittinunf.fuel.core.FuelError
import kotlin.time.TimeSource
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.RateLimitException
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.*

/** Metrics decorator for [ThorClient]. Uses composition instead of inheritance. */
class MonitoredThorClient(
    private val metrics: ThorClientMetrics,
    baseUrl: String,
    vararg headers: Pair<String, Any>,
) : ThorClient {

    private val delegate = DefaultThorClient(baseUrl, *headers)

    private suspend fun <T> withMetrics(method: String, path: String, block: suspend () -> T): T {
        val start = TimeSource.Monotonic.markNow()
        try {
            val result = block()
            metrics.recordResponseCode(method, path, "200")
            return result
        } catch (fuelErr: FuelError) {
            val statusCode = fuelErr.response.statusCode
            metrics.recordResponseCode(method, path, statusCode.toString())
            throw fuelErr
        } catch (ex: BlockNotFoundException) {
            metrics.recordResponseCode(method, path, "200")
            throw ex
        } catch (ex: RateLimitException) {
            metrics.recordResponseCode(method, path, "429")
            throw ex
        } catch (ex: Exception) {
            metrics.recordResponseCode(method, path, "unknown-exception")
            throw ex
        } finally {
            val durationMs = start.elapsedNow().inWholeMilliseconds.toDouble()
            metrics.observeRequestDuration(method, path, durationMs)
        }
    }

    override suspend fun getBlock(blockNumber: Long): Block =
        withMetrics("GET", "/blocks/{number}") { delegate.getBlock(blockNumber) }

    override suspend fun getBestBlock(): Block =
        withMetrics("GET", "/blocks/best") { delegate.getBestBlock() }

    override suspend fun getFinalizedBlock(): Block =
        withMetrics("GET", "/blocks/finalized") { delegate.getFinalizedBlock() }

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> =
        withMetrics("POST", "/logs/event") { delegate.getEventLogs(req) }

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> =
        withMetrics("POST", "/logs/transfer") { delegate.getVetTransfers(req) }

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        blockID: String,
    ): List<InspectionResult> =
        withMetrics("POST", "/accounts/*") { delegate.inspectClauses(clauses, blockID) }

    override suspend fun waitForBlock(blockNumber: Long): Block =
        withMetrics("GET", "/blocks/{number}/wait") { delegate.waitForBlock(blockNumber) }
}
