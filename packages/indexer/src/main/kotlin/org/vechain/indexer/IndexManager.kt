package org.vechain.indexer

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@EnableScheduling
@Component
class IndexManager(
    private val allIndexers: List<BaseIndexer>,
    private val statefulIndexers: List<StatefulIndexer<*, *>>,
    private val applicationContext: ApplicationContext,
    @Value("\${indexer.pruner.enabled}") private val prunerEnabled: Boolean
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {

        logger.info("Starting ${allIndexers.size} indexers")

        allIndexers.forEach { indexer ->
            try {
                indexer.startInCoroutine()
            } catch (e: Exception) {
                logger.error("Error starting indexer ${indexer.javaClass.simpleName}: ", e)
                // Exit the application if one of the indexers fails to start
                SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
            }
        }
    }

    @Scheduled(
        initialDelayString = "\${indexer.pruner.initialDelay:300000}",
        fixedRateString = "\${indexer.pruner.interval:300000}"
    )
    fun runPruners() {
        if (!prunerEnabled) return

        logger.info("Running pruners")

        statefulIndexers.forEach { indexer ->
            try {
                indexer.runPruner()
            } catch (e: Exception) {
                logger.error("Error running pruner for ${indexer.javaClass.simpleName}: ", e)
            }
        }
    }
}
