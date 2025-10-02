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
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.IndexerCoordinator
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionService

@Component
open class IndexManager(
    private val indexers: List<BlockIndexer>,
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

        IndexerCoordinator.launch(
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

    @EventListener(ContextClosedEvent::class)
    open fun onShutdown() {
        logger.info("Storing the last safe indexed blocks for all indexers")
        indexers.forEach { indexer ->
            try {
                indexerVersionService.updateLastSafeSyncedBlock(
                    indexerName = indexer.name,
                    block = indexer.previousBlock,
                )
            } catch (e: Exception) {
                logger.error("Failed to store last safe indexed block for ${indexer.name}", e)
            }
        }
    }
}
