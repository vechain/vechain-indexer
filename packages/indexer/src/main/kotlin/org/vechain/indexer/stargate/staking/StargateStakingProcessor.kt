package org.vechain.indexer.stargate.staking

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.backfill.BackfillCoordinatorFactory
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("stargate", "stargate-staking")
@Component
open class StargateStakingProcessor(
    private val service: StargateStakingService,
    repository: StargateStakingWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.stargate-staking:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.STARGATE_STAKING.COLLECTION,
            repository,
            state,
            checkpointProperties,
        ),
        IndexerNames.STARGATE_STAKING.NAME,
        version,
        processorMetrics,
        backfill?.create(StargateStakingIndexes.SET),
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
