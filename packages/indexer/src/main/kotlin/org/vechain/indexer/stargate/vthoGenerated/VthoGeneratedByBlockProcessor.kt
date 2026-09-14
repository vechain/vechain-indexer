package org.vechain.indexer.stargate.vthoGenerated

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("stargate", "vtho-generated-by-block")
@Component
open class VthoGeneratedByBlockProcessor(
    private val service: VthoGeneratedByBlockService,
    repository: VthoGeneratedWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.stargate-vtho-generated-by-block:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.VTHO_GENERATED_BY_BLOCK.COLLECTION,
            repository,
            state,
            checkpointProperties,
        ),
        IndexerNames.VTHO_GENERATED_BY_BLOCK.NAME,
        version,
        processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException("Expected entry of type IndexingResult.BlockResult")
        }

        val newRecord = service.processBlock(entry.block, entry.callResults())

        if (newRecord.isNotEmpty()) {
            service.save(newRecord)
        }
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        service.resetCache()
    }
}
