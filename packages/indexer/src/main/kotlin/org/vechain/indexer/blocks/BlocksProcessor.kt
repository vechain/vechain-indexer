package org.vechain.indexer.blocks

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

/**
 * Extends [BaseProcessor], not `BaseStatefulProcessor`: block headers are immutable and
 * append-only, so there is nothing to version and [BaseProcessor.rollback] already deletes forward
 * of the reorg point. The service holds no cache, so [resetProcessingState] needs no override.
 */
@Profile("blocks")
@Component
open class BlocksProcessor(
    repository: BlockRepository,
    private val service: BlocksService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.BLOCKS.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.BLOCKS.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException(
                "Expected IndexingResult.BlockResult with full block data"
            )
        }
        service.save(service.processBlock(entry.block))
    }
}
