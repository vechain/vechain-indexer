package org.vechain.indexer.config

import org.slf4j.LoggerFactory
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseIndexer
import org.vechain.indexer.BaseLogIndexer

@Component
class IndexManager(
    private val blockIndexers: List<BaseIndexer>,
    private val logIndexers: List<BaseLogIndexer>,
    private val applicationContext: ApplicationContext,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        logger.info("Starting ${blockIndexers.size} block indexers")
        logger.info("Starting ${logIndexers.size} log indexers")

        blockIndexers.forEach { indexer ->
            try {
                indexer.startInCoroutine()
            } catch (e: Exception) {
                logger.error("Error starting indexer ${indexer.javaClass.simpleName}: ", e)
                // Exit the application if one of the indexers fails to start
                SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
            }
        }

        logIndexers.forEach { indexer ->
            try {
                indexer.startInCoroutine()
            } catch (e: Exception) {
                logger.error("Error starting indexer ${indexer.javaClass.simpleName}: ", e)
                // Exit the application if one of the indexers fails to start
                SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
            }
        }
    }
}
