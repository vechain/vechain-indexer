package org.vechain.indexer.blocks

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("blocks")
@Component
open class BlocksProcessor(
    private val service: BlockTreeService,
    store: BlocksIndexerStore,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.blocks:1}") version: Int = 1,
) : PostgresProcessor(store, IndexerNames.BLOCKS.NAME, version, processorMetrics) {

    override fun bootstrap() {
        super.bootstrap()
        service.resetCache()
    }

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) { "Block must be a full block result." }
        service.save(service.processBlock(entry.block, entry.events))
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        service.resetCache()
    }
}
