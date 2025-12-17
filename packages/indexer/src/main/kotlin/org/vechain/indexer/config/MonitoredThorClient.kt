package org.vechain.indexer.config

import com.github.kittinunf.fuel.core.FuelError
import kotlin.time.TimeSource
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.exception.RateLimitException
import org.vechain.indexer.thor.client.DefaultThorClient
import org.vechain.indexer.thor.client.ExecuteAccountResponse
import org.vechain.indexer.thor.model.*

/**
 * Metrics decorator for [DefaultThorClient].
 *
 * Overrides `getBlock`/`getBlockUnexpanded` so the inherited
 * `waitForBlock`/`waitForBlockUnexpanded` (which retry internally) record metrics for each
 * underlying `/blocks/{revision}` call without separate "wait" metrics.
 */
class MonitoredThorClient(
    private val metrics: ThorClientMetrics,
    baseUrl: String,
    vararg headers: Pair<String, Any>,
) : DefaultThorClient(baseUrl, *headers) {

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

    override suspend fun getBlock(revision: BlockRevision): Block =
        withMetrics("GET", "/blocks/{revision}") { super.getBlock(revision) }

    override suspend fun getBlockUnexpanded(revision: BlockRevision): BlockUnexpanded =
        withMetrics("GET", "/blocks/{revision}?expanded=false") {
            super.getBlockUnexpanded(revision)
        }

    override suspend fun getEventLogs(req: EventLogsRequest): List<EventLog> =
        withMetrics("POST", "/logs/event") { super.getEventLogs(req) }

    override suspend fun getVetTransfers(req: TransferLogsRequest): List<TransferLog> =
        withMetrics("POST", "/logs/transfer") { super.getVetTransfers(req) }

    override suspend fun inspectClauses(
        clauses: List<Clause>,
        revision: BlockRevision?,
    ): List<InspectionResult> =
        withMetrics("POST", "/accounts/*") { super.inspectClauses(clauses, revision) }

    override suspend fun getAccountState(
        address: String,
        revision: BlockRevision?,
    ): ExecuteAccountResponse =
        withMetrics("GET", "/accounts/{address}") { super.getAccountState(address, revision) }
}
