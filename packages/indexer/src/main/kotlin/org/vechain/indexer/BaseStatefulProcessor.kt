package org.vechain.indexer

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
        resetProcessingState()
        checkpointService.saveCheckpoint(collectionName, blockNumber - 1)
        InlineVersionService.rollback(collectionName, blockNumber, mongoTemplate)
    }
}
