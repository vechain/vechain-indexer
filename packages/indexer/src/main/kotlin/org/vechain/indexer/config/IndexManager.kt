package org.vechain.indexer.config

import kotlinx.coroutines.CoroutineScope
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.vechain.indexer.Indexer
import org.vechain.indexer.orchestration.IndexerOrchestrator
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Component
open class IndexManager(
    private val indexers: List<Indexer>,
    private val appCoroutineScope: CoroutineScope,
    private val thorClient: ThorClient,
    private val applicationContext: ApplicationContext,
    private val indexerVersionService: IndexerVersionService,
    @param:Value("\${indexer.channel-batch-size}") private val channelBatchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    @EventListener(ApplicationReadyEvent::class)
    open fun start() {
        logger.info("Starting indexers")

        IndexerOrchestrator.launch(
                scope = appCoroutineScope,
                thorClient = thorClient,
                indexers = indexers,
                blockBatchSize = channelBatchSize,
            )
            .apply {
                invokeOnCompletion { throwable ->
                    if (throwable != null) {
                        logger.error("IndexerCoordinator terminated with error: ", throwable)
                        // Exit the application if the coordinator fails
                        SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
                    } else {
                        logger.info("IndexerCoordinator terminated normally")
                    }
                }
            }
    }

    @Scheduled(fixedDelay = 60 * 1000)
    open fun trackLastSyncedBlock() {
        logger.info("Storing last processed block for indexers")
        indexers.forEach { indexer ->
            try {
                indexerVersionService.updateLastSafeSyncedBlock(
                    indexerName = indexer.name,
                    block = indexer.getPreviousBlock(),
                )
            } catch (e: Exception) {
                logger.error("Failed to store last safe indexed block for ${indexer.name}", e)
            }
        }
    }

    @EventListener(ContextClosedEvent::class)
    open fun onShutdown() {
        logger.info("Shutting down indexers")
        indexers.forEach { indexer ->
            try {
                indexer.shutDown()
            } catch (e: Exception) {
                logger.error("Failed to close indexer ${indexer.name}", e)
            }
        }
        // Track last synced block one last time
        trackLastSyncedBlock()
        // Wait 5 seconds to allow indexers to shut down gracefully
        Thread.sleep(5000)
        logger.info("Indexers shut down complete")
    }
}
