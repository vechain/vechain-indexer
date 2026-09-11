package org.vechain.indexer.blocks

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.config.postgres.PostgresConfig

@Profile("blocks")
@Component
open class BlocksProcessor(
    private val service: BlockTreeService,
    repository: BlocksWriteRepository,
    processorMetrics: ProcessorMetrics,
) : BaseProcessor(BlocksIndexerStore(repository), IndexerNames.BLOCKS.NAME, processorMetrics) {

    @PostConstruct
    open fun bootstrap() {
        service.ensureVersion()
    }

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) { "Block must be a full block result." }
        service.save(service.processBlock(entry.block, entry.events))
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        service.resetCache()
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    override fun rollback(blockNumber: Long) {
        super.rollback(blockNumber)
    }
}
