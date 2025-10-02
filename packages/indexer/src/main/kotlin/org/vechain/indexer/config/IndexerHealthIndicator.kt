package org.vechain.indexer.config

import java.text.NumberFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

enum class HealthStatus() {
    UP,
    DOWN,
    UNKNOWN,
}

@Component
class IndexerHealthIndicator(
    private val indexers: List<Indexer>,
    @param:Value("\${indexer.healthcheck.inactive-threshold-syncing}")
    private val inactiveThresholdSyncing: Long,
    @param:Value("\${indexer.healthcheck.inactive-threshold-not-syncing}")
    private val inactiveThresholdNotSyncing: Long,
) : HealthIndicator {

    data class IndexerHealth(
        val indexerName: String,
        val status: HealthStatus,
        val statusDetails: String,
        val syncStatus: Status,
        val currentBlock: String,
    )

    override fun health(): Health {
        val key = "IndexersHealth"

        val indexerHealths =
            indexers.map { indexer ->
                val (status, statusDetails) = getIndexerHealth(indexer)
                IndexerHealth(
                    indexerName = indexer.name,
                    status = status,
                    statusDetails = statusDetails,
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

        val badIndexers = indexerHealths.filter { it.status == HealthStatus.DOWN }

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
    private fun getIndexerHealth(indexer: Indexer): Pair<HealthStatus, String> {
        if (indexer.status == Status.PRUNING) {
            return HealthStatus.UP to "Indexer is pruning"
        }

        if (indexer.status == Status.NOT_INITIALISED) {
            return HealthStatus.UNKNOWN to "Indexer is not initialised"
        }

        if (indexer.status == Status.INITIALISED) {
            return HealthStatus.UNKNOWN to "Indexer is initialised but not started"
        }

        val timeNow = LocalDateTime.now(ZoneOffset.UTC)

        // TODO: Needs to be cleaned up
        val timeout = inactiveThresholdSyncing

        val timeLastProcessed = if (indexer is BlockIndexer) indexer.timeLastProcessed else null
        return if (timeLastProcessed != null) {
            if (timeNow.minusSeconds(timeout) > timeLastProcessed) {
                HealthStatus.DOWN to
                    "Last processed at $timeLastProcessed which is more than $timeout seconds ago"
            } else {
                HealthStatus.UP to "Last processed at $timeLastProcessed"
            }
        } else {
            HealthStatus.UNKNOWN to "No last processed time available"
        }
    }
}
