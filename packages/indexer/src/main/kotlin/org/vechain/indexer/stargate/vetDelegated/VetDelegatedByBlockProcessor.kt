package org.vechain.indexer.stargate.vetDelegated

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

@Profile("stargate", "vet-delegated-by-block")
@Component
open class VetDelegatedByBlockProcessor(
    private val service: VetDelegatedByBlockService,
    repository: VetDelegatedWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.stargate-vet-delegated-by-block:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.VET_DELEGATED_BY_BLOCK.COLLECTION,
            repository,
            state,
            checkpointProperties,
        ),
        IndexerNames.VET_DELEGATED_BY_BLOCK.NAME,
        version,
        processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            return
        }

        val newRecords = service.processBlock(entry.block)

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        service.resetCache()
    }
}
