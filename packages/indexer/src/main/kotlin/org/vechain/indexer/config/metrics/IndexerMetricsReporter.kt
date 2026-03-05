package org.vechain.indexer.config.metrics

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
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
@ConditionalOnProperty(
    prefix = "management.prometheus.metrics.export",
    name = ["enabled"],
    havingValue = "true",
    matchIfMissing = true,
)
class IndexerMetricsReporter(
    private val indexers: List<Indexer>,
    private val metrics: IndexerHealthMetrics,
    private val thorClient: ThorClient,
    private val indexerHealthService: IndexerHealthService,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val previousBlockNumbers = ConcurrentHashMap<String, Long>()
    private val previousReportTimes = ConcurrentHashMap<String, Long>()
    private var gaugesInitialized = false

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:10000}")
    fun reportMetrics() {
        if (!gaugesInitialized) {
            initializeGauges()
            gaugesInitialized = true
        }

        val bestBlockNumber = fetchBestBlockNumber()

        indexers.forEach { indexer ->
            reportIndexerHealth(indexer)

            if (indexer is BlockIndexer) {
                reportBlockIndexerMetrics(indexer, bestBlockNumber)
            }
        }
    }

    private fun initializeGauges() {
        indexers.forEach { indexer ->
            if (indexer is BlockIndexer) {
                metrics.setIndexerSyncGap(indexer.name, 0L)
                metrics.setBlocksPerSecond(indexer.name, 0.0)
            }
        }
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
        val syncStatus = indexer.getStatus()
        metrics.setIndexerSyncStatus(indexer.name, syncStatus)

        logger.info(
            "INDEXER_STATUS indexer_name={} status={} status_code={}",
            indexer.name,
            syncStatus.name.toReadableEnumLabel(),
            syncStatus.toStatusCode().toInt(),
        )
    }

    private fun reportBlockIndexerMetrics(indexer: BlockIndexer, bestBlockNumber: Long?) {
        val currentBlockNumber = indexer.getCurrentBlockNumber()
        val status = indexer.getStatus()
        metrics.setIndexerCurrentBlock(indexer.name, currentBlockNumber)

        if (bestBlockNumber != null) {
            metrics.setIndexerSyncGap(indexer.name, maxOf(0L, bestBlockNumber - currentBlockNumber))
        }

        val isProcessing =
            status == Status.SYNCING ||
                status == Status.FAST_SYNCING ||
                status == Status.FULLY_SYNCED

        val now = System.nanoTime()
        var blocksPerSecond: Double? = null

        if (isProcessing) {
            val previousBlock = previousBlockNumbers[indexer.name]
            if (previousBlock != null && currentBlockNumber > previousBlock) {
                metrics.incrementBlocksProcessed(
                    indexer.name,
                    (currentBlockNumber - previousBlock).toDouble(),
                )
            }
            blocksPerSecond =
                computeBlocksPerSecond(indexer.name, currentBlockNumber, previousBlock, now)
            previousBlockNumbers[indexer.name] = currentBlockNumber
            previousReportTimes[indexer.name] = now
        } else {
            previousBlockNumbers.remove(indexer.name)
            previousReportTimes.remove(indexer.name)
        }

        metrics.setBlocksPerSecond(indexer.name, blocksPerSecond ?: 0.0)
    }

    private fun computeBlocksPerSecond(
        indexerName: String,
        currentBlockNumber: Long,
        previousBlock: Long?,
        now: Long,
    ): Double? {
        val previousTime = previousReportTimes[indexerName] ?: return null
        if (previousBlock == null || currentBlockNumber <= previousBlock) return null

        val elapsedSeconds = (now - previousTime) / 1_000_000_000.0
        if (elapsedSeconds <= 0) return null

        return (currentBlockNumber - previousBlock) / elapsedSeconds
    }
}
