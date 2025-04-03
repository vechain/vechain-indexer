package org.vechain.indexer.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.StatefulIndexer

@EnableScheduling
@Component
class PrunerScheduler(
    private val statefulIndexers: List<StatefulIndexer<*, *>>,
    @Value("\${indexer.pruner.enabled}") private val prunerEnabled: Boolean
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(
        initialDelayString = "\${indexer.pruner.initialDelay:300000}",
        fixedRateString = "\${indexer.pruner.interval:300000}"
    )
    fun runPruners() {
        if (!prunerEnabled) return

        logger.info("Running pruner on ${statefulIndexers.size} indexers")

        statefulIndexers.forEach { indexer ->
            try {
                indexer.runPruner()
            } catch (e: Exception) {
                logger.error("Error running pruner for ${indexer.javaClass.simpleName}: ", e)
            }
        }

        logger.info("Finished running pruners")
    }
}
