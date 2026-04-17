package org.vechain.indexer.transaction.count

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

@Profile("transactions")
@Component
open class TransactionCountProcessor(
    repository: TransactionCountSummaryRepository,
    private val service: TransactionCountService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
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
        val summary = service.processBlock(entry.block)
        service.save(summary)
    }
}
