package org.vechain.indexer.config.metrics

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.Indexer
import org.vechain.indexer.backfill.BackfillState
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.config.HealthStatus
import org.vechain.indexer.config.IndexerHealthService

// Per-indexer block, status and progress meters come from indexer-core's IndexerRunner.
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
    private val chainHead: ChainHead,
    private val indexerHealthService: IndexerHealthService,
    private val backfillState: BackfillState,
) {

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:10000}")
    fun reportMetrics() {
        chainHead.bestBlockNumber()?.let(metrics::setBestBlockNumber)
        // Silent until the first entry has counted the catalogue: a schema reporting 0 of 0
        // indexes reads as a schema with nothing left to rebuild.
        backfillState
            .progress()
            .filterValues { it.declared > 0 }
            .forEach { (schema, it) ->
                metrics.setBackfillPhase(schema, it.indexer, it.phase)
                metrics.setDeferrableIndexes(schema, it.indexer, it.standing, it.declared)
            }

        indexers.forEach(::reportIndexerHealth)
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
    }
}
