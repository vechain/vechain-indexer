package org.vechain.indexer.config

import org.slf4j.LoggerFactory
import org.springframework.boot.actuate.health.Status
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

        if (health.status != Status.UP) {
            logger.error(
                "Indexers are UNHEALTHY: {}",
                indexerHealths.filter { it.status == HealthStatus.DOWN },
            )
        }

        indexerHealths.forEach { indexerHealth ->
            when (indexerHealth.status) {
                HealthStatus.DOWN ->
                    logger.error(
                        "Indexer {} is UNHEALTHY: {} (syncStatus={}, currentBlock={})",
                        indexerHealth.indexerName,
                        indexerHealth.statusDetails,
                        indexerHealth.syncStatus,
                        indexerHealth.currentBlock,
                    )
                HealthStatus.UNKNOWN ->
                    logger.warn(
                        "Indexer {} health UNKNOWN: {} (syncStatus={}, currentBlock={})",
                        indexerHealth.indexerName,
                        indexerHealth.statusDetails,
                        indexerHealth.syncStatus,
                        indexerHealth.currentBlock,
                    )
                HealthStatus.UP ->
                    logger.debug(
                        "Indexer {} is healthy (syncStatus={}, currentBlock={})",
                        indexerHealth.indexerName,
                        indexerHealth.syncStatus,
                        indexerHealth.currentBlock,
                    )
            }
        }
    }
}
