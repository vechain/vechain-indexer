package org.vechain.indexer.config

import java.text.NumberFormat
import java.util.Locale
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer

enum class HealthStatus {
    UP,
    DOWN,
    UNKNOWN,
}

@Component
class IndexerHealthIndicator(
    private val indexers: List<Indexer>,
    private val indexerHealthService: IndexerHealthService,
) : HealthIndicator {

    data class IndexerHealth(
        val indexerName: String,
        val status: HealthStatus,
        val statusDetails: String,
        val syncStatus: org.vechain.indexer.Status,
        val currentBlock: String,
    )

    override fun health(): Health {
        val key = "IndexersHealth"

        val indexerHealths =
            indexers.map { indexer ->
                val (status, statusDetails) = indexerHealthService.getIndexerHealth(indexer)

                IndexerHealth(
                    indexerName = indexer.name,
                    status = status,
                    statusDetails = statusDetails,
                    syncStatus = indexer.getStatus(),
                    currentBlock =
                        if (indexer is BlockIndexer) {
                            NumberFormat.getNumberInstance(Locale.US)
                                .format(indexer.getCurrentBlockNumber())
                        } else {
                            "N/A"
                        },
                )
            }

        val badIndexers = indexerHealths.filter { it.status == HealthStatus.DOWN }

        return if (badIndexers.isNotEmpty()) {
            Health.down().withDetail(key, badIndexers).build()
        } else {
            Health.up().withDetail(key, indexerHealths).build()
        }
    }
}
