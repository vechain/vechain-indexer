package org.vechain.indexer.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.vechain.indexer.Indexer

@Component
class IndexManager(
    private val indexers: List<Indexer>,
    private val applicationContext: ApplicationContext,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @Bean fun appCoroutineScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @EventListener(ApplicationReadyEvent::class)
    fun start(appCoroutineScope: CoroutineScope) {
        logger.info("Starting ${indexers.size} indexers")

        indexers.forEach { indexer ->
            try {
                indexer.startInCoroutine(scope = appCoroutineScope)
            } catch (e: Exception) {
                logger.error("Error starting indexer ${indexer.javaClass.simpleName}: ", e)
                // Exit the application if one of the indexers fails to start
                SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
            }
        }
    }
}
