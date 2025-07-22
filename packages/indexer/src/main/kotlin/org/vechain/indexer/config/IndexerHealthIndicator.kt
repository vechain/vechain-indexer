package org.vechain.indexer.config

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

@Component
class IndexerHealthIndicator(private val indexers: List<Indexer>) : HealthIndicator {
    companion object {
        private const val STATUS_UP = "UP"
        private const val STATUS_DOWN = "DOWN"
        private const val PROCESS_TIMEOUT = 60L
        private const val SYNC_TIMEOUT = 300L
    }

    data class IndexerHealth(
        val indexerName: String,
        val status: String,
        val syncStatus: Status,
        val currentBlock: String,
    )

    override fun health(): Health {
        val key = "IndexersHealth"

        val indexerHealths =
            indexers.map { indexer ->
                IndexerHealth(
                    indexerName = indexer.name,
                    status = getIndexerHealth(indexer),
                    syncStatus = indexer.status,
                    currentBlock =
                        if (indexer is BlockIndexer) {
                            NumberFormat.getNumberInstance(Locale.US)
                                .format(indexer.currentBlockNumber)
                        } else {
                            "N/A"
                        },
                )
            }

        val badIndexers = indexerHealths.filter { it.status == STATUS_DOWN }

        return if (badIndexers.isNotEmpty()) {
            Health.down().withDetail(key, badIndexers).build()
        } else {
            Health.up().withDetail(key, indexerHealths).build()
        }
    }

    /**
     * Get the health status of the indexer If the indexer is syncing we use the SYNC_TIMEOUT to
     * determine if it is down If the indexer is not syncing we use the PROCESS_TIMEOUT to determine
     * if it is down
     */
    private fun getIndexerHealth(indexer: Indexer): String {
        val timeNow = LocalDateTime.now(ZoneOffset.UTC)

        val timeout =
            if (indexer.status == Status.SYNCING) {
                SYNC_TIMEOUT
            } else {
                PROCESS_TIMEOUT
            }

        val timeLastProcessed = if (indexer is BlockIndexer) indexer.timeLastProcessed else null
        return if (timeLastProcessed != null) {
            if (timeNow.minusSeconds(timeout) > timeLastProcessed) {
                STATUS_DOWN
            } else {
                STATUS_UP
            }
        } else {
            STATUS_UP
        }
    }
}
