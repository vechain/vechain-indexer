package org.vechain.indexer

import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

abstract class BaseStatefulProcessor(
    repository: BaseIndexedRepository<*, *>,
    private val mongoTemplate: MongoTemplate,
    indexerName: String,
    checkpointService: CheckpointService,
    collectionName: String,
    processorMetrics: ProcessorMetrics,
) : BaseProcessor(repository, indexerName, checkpointService, collectionName, processorMetrics) {
    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        val start = TimeSource.Monotonic.markNow()
        resetProcessingState()
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        InlineVersionService.rollback(
            collectionName,
            blockNumber,
            mongoTemplate,
            VersionedDocumentInitialVersions.forCollection(collectionName),
        )
        rewindLastObservedBlock(blockNumber - 1)
        val elapsed = start.elapsedNow()
        if (elapsed > 1.seconds) {
            startupLogger.warn(
                "{}: versioned rollback for {} at block {} took {}",
                indexerName,
                collectionName,
                blockNumber,
                elapsed,
            )
        } else {
            startupLogger.info(
                "{}: versioned rollback for {} at block {} completed in {}",
                indexerName,
                collectionName,
                blockNumber,
                elapsed,
            )
        }
    }
}
