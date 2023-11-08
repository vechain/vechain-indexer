package org.vechain.indexer

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

@Component
class IndexManager(
    private val indexers: List<DebugIndexer>,
    private val applicationContext: ApplicationContext
) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        logger.info("Starting ${indexers.size} indexers")

        indexers.forEach { indexer ->
            try {
                indexer.startInCoroutine(10L)
            } catch (e: Exception) {
                logger.error("Error starting indexer ${indexer.javaClass.simpleName}: ", e)
                // Exit the application if one of the indexers fails to start
                SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
            }
        }
    }
}
