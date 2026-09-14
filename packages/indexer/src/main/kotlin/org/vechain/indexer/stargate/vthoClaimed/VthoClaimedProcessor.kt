package org.vechain.indexer.stargate.vthoClaimed

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

@Profile("stargate", "vtho-claimed")
@Component
open class VthoClaimedProcessor(
    private val service: VthoClaimedService,
    repository: VthoClaimedWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.stargate-vtho-claimed:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.VTHO_CLAIMED.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.VTHO_CLAIMED.NAME,
        version,
        processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val update = service.processEvents(entry.events())
        if (!update.isEmpty()) service.save(update)
    }

    override fun resetProcessingState() {
        super.resetProcessingState()
        service.resetCache()
    }
}
