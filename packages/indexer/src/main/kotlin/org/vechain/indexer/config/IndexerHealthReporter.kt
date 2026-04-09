package org.vechain.indexer.config

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class IndexerHealthReporter(private val indexerHealthIndicator: IndexerHealthIndicator) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedDelayString = "\${indexer.healthcheck.report-interval-ms:60000}")
    fun reportIndexerHealth() {
        val health = indexerHealthIndicator.health()

        val indexerHealths =
            (health.details["IndexersHealth"] as? Collection<*>)?.mapNotNull {
                it as? IndexerHealthIndicator.IndexerHealth
            } ?: emptyList()

        val grouped = indexerHealths.groupBy { it.status }

        grouped[HealthStatus.DOWN]?.let { down -> logger.error("Unhealthy indexers: {}", down) }

        grouped[HealthStatus.UNKNOWN]?.let { unknown ->
            logger.warn("Unknown health indexers: {}", unknown)
        }

        grouped[HealthStatus.UP]?.let { up ->
            logger.debug("Healthy indexers: {}/{}", up.size, indexerHealths.size)
        }
    }
}
