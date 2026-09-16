package org.vechain.indexer.config

import java.time.LocalDateTime
import java.time.ZoneOffset
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.Status

/** Whole-process liveness: DOWN only once every running indexer has gone quiet. */
@Component
class IndexerLivenessHealthIndicator(
    private val indexers: List<Indexer>,
    private val indexBootstrapState: IndexBootstrapState,
    @param:Value("\${indexer.healthcheck.stall-threshold-seconds}")
    private val stallThresholdSeconds: Long,
) : HealthIndicator {

    override fun health(): Health {
        val bootstrap = indexBootstrapState.snapshot().status
        if (bootstrap != IndexBootstrapState.Status.READY) {
            return up("Bootstrap is $bootstrap; indexers have not started")
        }

        // An indexer parked before or after its sync loop has no progress to measure. Reporting UP
        // when none is running keeps startup and shutdown out of the kill path.
        val running =
            indexers.filterIsInstance<BlockIndexer>().filter { it.getStatus() in RUNNING_STATUSES }
        if (running.isEmpty()) return up("No indexer is running")

        val lastProgress = running.maxOf { it.timeLastProcessed }
        val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusSeconds(stallThresholdSeconds)

        return if (lastProgress < cutoff) {
            Health.down()
                .withDetail(
                    "message",
                    "No indexer has progressed since $lastProgress, more than " +
                        "$stallThresholdSeconds seconds ago",
                )
                .withDetail("runningIndexers", running.size)
                .build()
        } else {
            up("Last progress by any indexer at $lastProgress")
        }
    }

    private fun up(message: String): Health = Health.up().withDetail("message", message).build()

    companion object {
        private val RUNNING_STATUSES =
            setOf(Status.SYNCING, Status.FAST_SYNCING, Status.FULLY_SYNCED)
    }
}
