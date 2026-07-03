package org.vechain.indexer.config

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
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
import org.vechain.indexer.BaseProcessor
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
    private val processors: List<BaseProcessor>,
    private val collectionConfigs: List<CollectionConfig>,
    private val indexerVersionCollectionConfig: IndexerVersionCollectionConfig,
    private val indexBootstrapState: IndexBootstrapState,
    private val appCoroutineScope: CoroutineScope,
    private val thorClient: ThorClient,
    private val metrics: IndexerHealthMetrics,
    private val applicationContext: ApplicationContext,
    @param:Value("\${indexer.channel-batch-size}") private val channelBatchSize: Int,
    @param:Value("\${indexer.catch-up-interval-seconds}") private val catchUpIntervalSeconds: Long,
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
            val initializerCount = collectionConfigs.size
            indexBootstrapState.markRunning(initializerCount)

            try {
                val bootstrapStart = TimeSource.Monotonic.markNow()
                // IndexerVersion must bootstrap first: other configs' initCollection() writes to
                // it via IndexerVersionService.checkAndResetCollectionIfVersionChanged.
                indexerVersionCollectionConfig.initCollection()
                indexerVersionCollectionConfig.removeStaleIndexes()
                indexerVersionCollectionConfig.createPendingIndexes()
                collectionConfigs
                    .filter { it !== indexerVersionCollectionConfig }
                    .sortedBy { it.modelObj.simpleName }
                    .forEach {
                        val start = TimeSource.Monotonic.markNow()
                        it.initCollection()
                        it.removeStaleIndexes()
                        it.createPendingIndexes()
                        logger.debug(
                            "Collection bootstrap for {} completed in {}",
                            it.modelObj.simpleName,
                            start.elapsedNow(),
                        )
                    }
                logger.info(
                    "Bootstrapped {} collections in {}",
                    collectionConfigs.size,
                    bootstrapStart.elapsedNow(),
                )
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

        // Soft stop: flip each indexer's status so the next processBlock() throws
        // CancellationException. Any in-flight call that already passed the status check is
        // allowed to complete naturally.
        indexers.forEach { indexer ->
            try {
                indexer.shutDown()
            } catch (e: Exception) {
                logger.error("Failed to close indexer ${indexer.name}", e)
            }
        }

        // Hard stop: cancel the runner's scope and wait for all in-flight coroutines to finish.
        // Without the join, flushCheckpoint() could read lastObservedBlock from a processor whose
        // process() coroutine has not yet returned — then a post-flush process() could advance
        // lastObservedBlock while the throttle prevents trySaveCheckpoint from persisting it,
        // leaving the on-disk checkpoint stale. Bounded by SHUTDOWN_DRAIN_TIMEOUT so a stuck
        // coroutine cannot hold the container past its SIGTERM grace period.
        val drained = runBlocking {
            withTimeoutOrNull(SHUTDOWN_DRAIN_TIMEOUT) {
                appCoroutineScope.coroutineContext.job.cancelAndJoin()
                true
            } ?: false
        }
        if (!drained) {
            logger.warn(
                "Coroutine scope did not drain within {}; flushing checkpoints anyway",
                SHUTDOWN_DRAIN_TIMEOUT,
            )
        }

        // All in-flight process() calls have returned (or the drain timed out). Force-flush each
        // processor's checkpoint past the throttle, closing the up-to-saveIntervalSeconds gap
        // between in-memory progress and persisted checkpoint on a clean restart.
        processors.forEach { processor ->
            try {
                processor.flushCheckpoint()
            } catch (e: Exception) {
                logger.error(
                    "Failed to flush checkpoint on shutdown for ${processor::class.simpleName}",
                    e,
                )
            }
        }

        // Publish final metrics after the runner has stopped so values reflect the genuine
        // last-processed state.
        indexers.forEach { indexer ->
            try {
                publishShutdownMetrics(indexer)
            } catch (e: Exception) {
                logger.error("Failed to publish shutdown metrics for indexer ${indexer.name}", e)
            }
        }

        logger.info("Indexers shut down complete")
    }

    companion object {
        private val SHUTDOWN_DRAIN_TIMEOUT = 30.seconds
    }

    private fun publishShutdownMetrics(indexer: Indexer) {
        metrics.setComponentHealth(indexer.name, "indexer", 0.0)
        metrics.setIndexerSyncStatus(indexer.name, indexer.getStatus())

        if (indexer is BlockIndexer) {
            metrics.setIndexerCurrentBlock(indexer.name, indexer.getCurrentBlockNumber())
        }
    }
}
