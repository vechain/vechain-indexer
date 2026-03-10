package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository

@Profile("explorer", "average-fees-per-user")
@Component
open class AverageFeesPerUserProcessor(
    repository: AverageFeesPerUserRepository,
    mongoTemplate: MongoTemplate,
    private val service: AverageFeesPerUserService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.AVERAGE_FEES_PER_USER.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.AVERAGE_FEES_PER_USER.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException(
                "Expected IndexingResult.BlockResult with full block data"
            )
        }

        val (updated, existing, newMarkers) = service.processBlock(entry.block)
        if (updated.isNotEmpty()) {
            service.save(updated, existing, newMarkers)
        }
    }

    @Transactional(rollbackFor = [Exception::class])
    override fun rollback(blockNumber: Long) {
        service.clearProcessingState()
        service.deleteMarkersFromBlock(blockNumber)
        super.rollback(blockNumber)
    }
}
