package org.vechain.indexer.config

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
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
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexerRunner
import org.vechain.indexer.Status
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.client.ThorClient

@Component
open class IndexManager(
    private val indexers: List<Indexer>,
    private val appCoroutineScope: CoroutineScope,
    private val thorClient: ThorClient,
    private val applicationContext: ApplicationContext,
    private val checkpointService: CheckpointService,
    @param:Value("\${indexer.channel-batch-size}") private val channelBatchSize: Int,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)
    private val nameToCollection = IndexerNames.nameToCollection()

    @EventListener(ApplicationReadyEvent::class)
    open fun start() {
        logger.info("Starting indexers")

        IndexerRunner.launch(
                scope = appCoroutineScope,
                thorClient = thorClient,
                indexers = indexers,
                blockBatchSize = channelBatchSize,
            )
            .apply {
                invokeOnCompletion { throwable ->
                    if (throwable != null) {
                        logger.error("IndexerRunner terminated with error: ", throwable)
                        // Exit the application if the coordinator fails
                        SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
                    } else {
                        logger.info("IndexerRunner terminated normally")
                    }
                }
            }
    }

    @Scheduled(fixedDelay = 60_000)
    open fun saveCheckpoints() {
        val activeSyncStatuses = setOf(Status.FAST_SYNCING, Status.SYNCING, Status.FULLY_SYNCED)

        indexers.forEach { indexer ->
            try {
                if (indexer.getStatus() !in activeSyncStatuses) return@forEach

                val collection = nameToCollection[indexer.name] ?: return@forEach

                val lastProcessedBlock = indexer.getCurrentBlockNumber() - 1
                if (lastProcessedBlock >= 0) {
                    checkpointService.saveCheckpoint(collection, lastProcessedBlock)
                }
            } catch (e: Exception) {
                logger.error("Failed to save checkpoint for ${indexer.name}", e)
            }
        }
    }

    @EventListener(ContextClosedEvent::class)
    open fun onShutdown() {
        logger.info("Shutting down indexers")

        saveCheckpoints()

        indexers.forEach { indexer ->
            try {
                indexer.shutDown()
            } catch (e: Exception) {
                logger.error("Failed to close indexer ${indexer.name}", e)
            }
        }
        // Cancel the coroutine scope to stop all running indexers
        appCoroutineScope.cancel()

        logger.info("Indexers shut down complete")
    }
}
