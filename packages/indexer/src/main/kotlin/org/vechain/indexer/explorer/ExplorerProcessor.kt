package org.vechain.indexer.explorer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("explorer")
@Component
open class ExplorerProcessor(
    private val blockUsageService: BlockUsageService,
    private val feesService: AverageFeesPerUserService,
    private val repository: ExplorerWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.explorer:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.EXPLORER.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.EXPLORER.NAME,
        version,
        processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected IndexingResult.BlockResult with full block data"
        }
        val usage = blockUsageService.processBlock(entry.block)
        val fees = feesService.processBlock(entry.block)
        repository.save(usage, fees?.updatedSummary, fees?.newOrigins.orEmpty())
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        blockUsageService.resetCache()
    }
}
