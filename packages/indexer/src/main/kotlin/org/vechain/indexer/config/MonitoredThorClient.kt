package org.vechain.indexer.config

import com.github.kittinunf.fuel.core.FuelError
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.RateLimitException
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.*

/** Metrics decorator for [ThorClient]. */
class MonitoredThorClient(
    private val metrics: ThorClientMetrics,
    baseUrl: String,
    vararg headers: Pair<String, Any>,
) : DefaultThorClient(baseUrl, *headers) {

    private suspend fun <T> withMetrics(method: String, path: String, block: suspend () -> T): T {
        val start = System.nanoTime()
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
            val durationMs = (System.nanoTime() - start) / 1_000_000.0
            metrics.observeRequestDuration(method, path, durationMs)
        }
    }

    override suspend fun getBlock(blockNumber: Long): Block =
        withMetrics("GET", "/blocks/{number}") { super.getBlock(blockNumber) }

    override suspend fun getBestBlock(): Block =
        withMetrics("GET", "/blocks/best") { super.getBestBlock() }

    override suspend fun getFinalizedBlock(): Block =
        withMetrics("GET", "/blocks/finalized") { super.getFinalizedBlock() }

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> =
        withMetrics("POST", "/logs/event") { super.getEventLogs(req) }

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> =
        withMetrics("POST", "/logs/transfer") { super.getVetTransfers(req) }

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        blockID: String,
    ): List<InspectionResult> =
        withMetrics("POST", "/accounts/*") { super.inspectClauses(clauses, blockID) }
}
