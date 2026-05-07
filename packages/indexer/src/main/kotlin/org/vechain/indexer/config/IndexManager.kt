package org.vechain.indexer.config

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.vechain.indexer.BlockIndexer
import org.vechain.indexer.Indexer
import org.vechain.indexer.IndexerRunner
import org.vechain.indexer.config.metrics.IndexerHealthMetrics
import org.vechain.indexer.config.mongo.CollectionConfig
import org.vechain.indexer.history.DelegationLifecycleHistoryService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.version.IndexerVersionCollectionConfig

@Component
open class IndexManager(
    private val indexers: List<Indexer>,
    private val collectionConfigs: List<CollectionConfig>,
    private val indexerVersionCollectionConfig: IndexerVersionCollectionConfig,
    private val indexBootstrapState: IndexBootstrapState,
    private val appCoroutineScope: CoroutineScope,
    private val thorClient: ThorClient,
    private val metrics: IndexerHealthMetrics,
    private val applicationContext: ApplicationContext,
    @param:Value("\${indexer.channel-batch-size}") private val channelBatchSize: Int,
    @param:Value("\${indexer.catch-up-interval-seconds:5}")
    private val catchUpIntervalSeconds: Long,
    @param:Autowired(required = false)
    private val delegationLifecycleHistoryService: DelegationLifecycleHistoryService? = null,
) {
    private val logger = LoggerFactory.getLogger(this::class.java)

    init {
        require(catchUpIntervalSeconds > 0) { "indexer.catch-up-interval-seconds must be > 0" }
    }

    @EventListener(ApplicationReadyEvent::class)
    open fun start() {
        logger.info("Application ready. Starting collection and index bootstrap in background")

        appCoroutineScope.launch {
            val initializerCount = collectionConfigs.size + 1
            indexBootstrapState.markRunning(initializerCount)

            try {
                indexerVersionCollectionConfig.ensureIndexes()
                collectionConfigs
                    .sortedBy { it.modelObj.simpleName }
                    .forEach {
                        val start = TimeSource.Monotonic.markNow()
                        it.initCollection()
                        it.removeStaleIndexes()
                        it.createPendingIndexes()
                        logger.info(
                            "Collection bootstrap for {} completed in {}",
                            it.modelObj.simpleName,
                            start.elapsedNow(),
                        )
                    }
                indexBootstrapState.markReady(initializerCount)

                delegationLifecycleHistoryService?.let {
                    logger.info("Preloading delegation lifecycle state")
                    val start = TimeSource.Monotonic.markNow()
                    it.preload()
                    logger.info("Delegation lifecycle preload completed in {}", start.elapsedNow())
                }

                logger.info("Collection bootstrap complete. Starting indexers")
                startIndexers()
            } catch (throwable: Throwable) {
                indexBootstrapState.markFailed(throwable)
                logger.error("Collection bootstrap failed", throwable)
                SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
            }
        }
    }

    private fun startIndexers() {
        // Override the upstream 5-minute default for catchUpInterval. With the upstream default,
        // LogsIndexers (FastSyncableIndexer) that finish fast sync early stay parked at
        // READY_TO_SYNC for the rest of the slice before the runner re-classifies and lets them
        // join steady-state. A short interval keeps the catch-up loop reactive — re-evaluation
        // overhead per tick is negligible.
        IndexerRunner.launch(
                scope = appCoroutineScope,
                thorClient = thorClient,
                indexers = indexers,
                blockBatchSize = channelBatchSize,
                catchUpInterval = catchUpIntervalSeconds.seconds,
            )
            .apply {
                invokeOnCompletion { throwable ->
                    if (throwable != null) {
                        logger.error("IndexerRunner terminated with error: ", throwable)
                        SpringApplication.exit(applicationContext, ExitCodeGenerator { 1 })
                    } else {
                        logger.info("IndexerRunner terminated normally")
                    }
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
            } finally {
                try {
                    publishShutdownMetrics(indexer)
                } catch (e: Exception) {
                    logger.error(
                        "Failed to publish shutdown metrics for indexer ${indexer.name}",
                        e,
                    )
                }
            }
        }
        // Cancel the coroutine scope to stop all running indexers
        appCoroutineScope.cancel()

        logger.info("Indexers shut down complete")
    }

    private fun publishShutdownMetrics(indexer: Indexer) {
        metrics.setComponentHealth(indexer.name, "indexer", 0.0)
        metrics.setIndexerSyncStatus(indexer.name, indexer.getStatus())

        if (indexer is BlockIndexer) {
            metrics.setIndexerCurrentBlock(indexer.name, indexer.getCurrentBlockNumber())
            metrics.setBlocksPerSecond(indexer.name, 0.0)
        }
    }
}
