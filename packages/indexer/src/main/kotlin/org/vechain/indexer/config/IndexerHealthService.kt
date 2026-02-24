package org.vechain.indexer.config

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

@Service
class IndexerHealthService(
    @param:Value("\${indexer.healthcheck.inactive-threshold-syncing}")
    private val inactiveThresholdSyncing: Long,
    @param:Value("\${indexer.healthcheck.inactive-threshold-not-syncing}")
    private val inactiveThresholdNotSyncing: Long,
) {

    /**
     * Get the health status of the indexer. If the indexer is syncing we use the syncing threshold
     * to determine if it is down. If the indexer is not syncing we use the not-syncing threshold to
     * determine if it is down.
     */
    fun getIndexerHealth(indexer: Indexer): Pair<HealthStatus, String> {
        when (indexer.getStatus()) {
            Status.PRUNING -> return HealthStatus.UP to "Indexer is pruning"
            Status.NOT_INITIALISED -> return HealthStatus.UP to "Indexer is not initialised"
            Status.INITIALISED -> return HealthStatus.UP to "Indexer is initialised but not started"
            Status.SHUT_DOWN -> return HealthStatus.DOWN to "Indexer is shut down"
            else -> {
                // continue to check last processed time
            }
        }

        val timeNow = LocalDateTime.now(ZoneOffset.UTC)

        val timeout =
            if (
                indexer.getStatus() == Status.SYNCING || indexer.getStatus() == Status.FAST_SYNCING
            ) {
                inactiveThresholdSyncing
            } else {
                inactiveThresholdNotSyncing
            }

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
