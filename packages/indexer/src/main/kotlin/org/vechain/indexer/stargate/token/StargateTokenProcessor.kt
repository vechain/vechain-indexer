package org.vechain.indexer.stargate.token

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

@Profile("stargate", "stargate-token")
@Component
open class StargateTokenProcessor(
    private val service: StargateTokenService,
    stargateTokenRepository: StargateTokenWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.stargate-token:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.STARGATE_TOKEN.COLLECTION,
            stargateTokenRepository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.STARGATE_TOKEN.NAME,
        version,
        processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected entry of type IndexingResult.BlockResult (full block entry required)"
        }
        val updated = service.processBlock(entry.block, entry.events())
        if (updated.isNotEmpty()) service.save(updated)
    }

    override fun resetProcessingState() {
        service.invalidateCache()
    }
}
