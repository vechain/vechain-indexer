package org.vechain.indexer.config.metrics

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status
import org.vechain.indexer.config.HealthStatus
import org.vechain.indexer.config.IndexerHealthService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision

@Component
class IndexerMetricsReporter(
    private val indexers: List<Indexer>,
    private val metrics: IndexerHealthMetrics,
    private val thorClient: ThorClient,
    private val indexerHealthService: IndexerHealthService,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val previousBlockNumbers = ConcurrentHashMap<String, Long>()
    private val previousReportTimes = ConcurrentHashMap<String, Long>()

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:60000}")
    fun reportMetrics() {
        val bestBlockNumber = fetchBestBlockNumber()

        val syncingEtas = mutableMapOf<String, Double>()
        val initialisedIndexerNames = mutableListOf<String>()

        indexers.forEach { indexer ->
            reportIndexerHealth(indexer)

            if (indexer.getStatus() == Status.INITIALISED) {
                initialisedIndexerNames.add(indexer.name)
            }

            if (indexer is BlockIndexer) {
                val eta = reportBlockIndexerMetrics(indexer, bestBlockNumber)
                if (eta != null && indexer.getStatus() == Status.SYNCING) {
                    syncingEtas[indexer.name] = eta
                }
            }
        }

        estimateInitialisedEtas(initialisedIndexerNames, syncingEtas)
    }

    private fun fetchBestBlockNumber(): Long? {
        val bestBlockNumber =
            try {
                runBlocking { thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST).number }
            } catch (e: Exception) {
                logger.warn(
                    "Failed to fetch best block for metrics for revision {}",
                    BlockRevision.Keyword.BEST,
                    e,
                )
                null
            }

        if (bestBlockNumber != null) {
            metrics.setBestBlockNumber(bestBlockNumber)
        }

        return bestBlockNumber
    }

    private fun reportIndexerHealth(indexer: Indexer) {
        val (status, _) = indexerHealthService.getIndexerHealth(indexer)
        metrics.setComponentHealth(
            indexer.name,
            "indexer",
            when (status) {
                HealthStatus.UP -> 1.0
                HealthStatus.DOWN -> 0.0
                HealthStatus.UNKNOWN -> -1.0
            },
        )
        metrics.setIndexerSyncStatus(indexer.name, indexer.getStatus())
    }

    private fun reportBlockIndexerMetrics(indexer: BlockIndexer, bestBlockNumber: Long?): Double? {
        val currentBlockNumber = indexer.getCurrentBlockNumber()
        metrics.setIndexerCurrentBlockByStatus(
            indexer.name,
            currentBlockNumber,
            indexer.getStatus(),
        )

        if (bestBlockNumber != null) {
            metrics.setIndexerSyncGap(indexer.name, bestBlockNumber - currentBlockNumber)
        }

        val previousBlock = previousBlockNumbers.put(indexer.name, currentBlockNumber)
        if (previousBlock != null && currentBlockNumber > previousBlock) {
            metrics.incrementBlocksProcessed(
                indexer.name,
                (currentBlockNumber - previousBlock).toDouble(),
            )
        }

        val now = System.nanoTime()
        val eta = computeEta(indexer, bestBlockNumber, currentBlockNumber, previousBlock, now)
        if (eta != null) {
            metrics.setEstimatedTimeToSync(indexer.name, eta)
        }
        previousReportTimes[indexer.name] = now
        return eta
    }

    private fun computeEta(
        indexer: BlockIndexer,
        bestBlockNumber: Long?,
        currentBlockNumber: Long,
        previousBlock: Long?,
        now: Long,
    ): Double? {
        if (indexer.getStatus() == Status.FULLY_SYNCED) return 0.0

        val syncGap = bestBlockNumber?.minus(currentBlockNumber) ?: return null
        val previousTime = previousReportTimes[indexer.name] ?: return null
        if (previousBlock == null || currentBlockNumber <= previousBlock) return null

        val elapsedSeconds = (now - previousTime) / 1_000_000_000.0
        if (elapsedSeconds <= 0) return null

        val blocksPerSecond = (currentBlockNumber - previousBlock) / elapsedSeconds
        return syncGap / blocksPerSecond
    }

    private fun estimateInitialisedEtas(
        initialisedIndexerNames: List<String>,
        syncingEtas: Map<String, Double>,
    ) {
        if (initialisedIndexerNames.isEmpty() || syncingEtas.isEmpty()) return
        val maxEta = syncingEtas.values.filter { it > 0.0 }.maxOrNull() ?: return
        initialisedIndexerNames.forEach { metrics.setEstimatedTimeToSync(it, maxEta) }
    }
}
