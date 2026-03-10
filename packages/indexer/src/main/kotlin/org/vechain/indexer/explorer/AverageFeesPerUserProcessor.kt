package org.vechain.indexer.explorer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.explorer.repository.AverageFeesPerUserRepository

@Profile("explorer", "average-fees-per-user")
@Component
open class AverageFeesPerUserProcessor(
    repository: AverageFeesPerUserRepository,
    private val service: AverageFeesPerUserService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
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

        val records = service.processBlock(entry.block)
        if (records.isNotEmpty()) {
            service.save(records)
        }
    }

    override fun rollback(blockNumber: Long) {
        service.clearProcessingState()
        super.rollback(blockNumber)
    }
}
