package org.vechain.indexer.config.metrics

import java.util.concurrent.ConcurrentHashMap
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status
import org.vechain.indexer.backfill.BackfillState
import org.vechain.indexer.chain.ChainHead
import org.vechain.indexer.config.HealthStatus
import org.vechain.indexer.config.IndexerHealthService

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

    private val previousBlockNumbers = ConcurrentHashMap<String, Long>()

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:10000}")
    fun reportMetrics() {
        val bestBlockNumber = chainHead.bestBlockNumber()
        bestBlockNumber?.let(metrics::setBestBlockNumber)
        // Silent until the first entry has counted the catalogue: a schema reporting 0 of 0
        // indexes reads as a schema with nothing left to rebuild.
        backfillState
            .progress()
            .filterValues { it.declared > 0 }
            .forEach { (schema, it) ->
                metrics.setBackfillPhase(schema, it.indexer, it.phase)
                metrics.setDeferrableIndexes(schema, it.indexer, it.standing, it.declared)
            }

        indexers.forEach { indexer ->
            reportIndexerHealth(indexer)

            if (indexer is BlockIndexer) {
                reportBlockIndexerMetrics(indexer, bestBlockNumber)
            }
        }
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

    // Sync gap is intentionally not emitted as its own gauge: it's just
    // `thor_best_block_number - indexer_current_block` in PromQL, and
    // blocks-per-second is `rate(indexer_blocks_processed_total[1m])`.
    // We keep the counter increment here so those rate() queries work.
    private fun reportBlockIndexerMetrics(indexer: BlockIndexer, bestBlockNumber: Long?) {
        val status = indexer.getStatus()
        if (status == Status.NOT_INITIALISED) {
            previousBlockNumbers.remove(indexer.name)
            return
        }
        val currentBlockNumber = indexer.getCurrentBlockNumber()
        metrics.setIndexerCurrentBlock(indexer.name, currentBlockNumber)

        val isProcessing =
            status == Status.SYNCING ||
                status == Status.FAST_SYNCING ||
                status == Status.FULLY_SYNCED

        if (isProcessing) {
            val previousBlock = previousBlockNumbers[indexer.name]
            if (previousBlock != null && currentBlockNumber > previousBlock) {
                metrics.incrementBlocksProcessed(
                    indexer.name,
                    (currentBlockNumber - previousBlock).toDouble(),
                )
            }
            previousBlockNumbers[indexer.name] = currentBlockNumber
        } else {
            previousBlockNumbers.remove(indexer.name)
        }
    }
}
