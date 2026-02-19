package org.vechain.indexer.config.metrics

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
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

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:60000}")
    fun reportMetrics() {
        val bestBlockNumber =
            try {
                runBlocking { thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST).number }
            } catch (e: Exception) {
                logger.warn("Failed to fetch best block for metrics for revision {}", BlockRevision.Keyword.BEST, e)
                null
            }

        if (bestBlockNumber != null) {
            metrics.setBestBlockNumber(bestBlockNumber)
        }

        indexers.forEach { indexer ->
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
            if (indexer is BlockIndexer) {
                val currentBlockNumber = indexer.getCurrentBlockNumber()
                metrics.setIndexerCurrentBlockByStatus(
                    indexer.name,
                    currentBlockNumber,
                    indexer.getStatus(),
                )
                if (bestBlockNumber != null) {
                    metrics.setIndexerSyncGap(indexer.name, bestBlockNumber - currentBlockNumber)
                }
            }
        }
    }
}
