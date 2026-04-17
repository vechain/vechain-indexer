package org.vechain.indexer.transaction.count

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

@Profile("transactions", "transaction-count")
@Component
open class TransactionCountProcessor(
    repository: TransactionCountSummaryRepository,
    mongoTemplate: MongoTemplate,
    private val service: TransactionCountService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.TRANSACTION_COUNT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.TRANSACTION_COUNT.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException(
                "Expected IndexingResult.BlockResult with full block data"
            )
        }
        val result = service.processBlock(entry.block)
        if (result.shouldPersist) {
            service.save(result.current, result.previous)
        }
    }

    override fun rollback(blockNumber: Long) {
        service.resetCache()
        super.rollback(blockNumber)
    }
}
