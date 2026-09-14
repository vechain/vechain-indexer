package org.vechain.indexer.stargate.rewards

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
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository

@Profile("token-reward")
@Component
open class TokenRewardProcessor(
    private val service: TokenRewardService,
    repository: TokenRewardWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.token-rewards:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.TOKEN_REWARD.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.TOKEN_REWARD.NAME,
        version,
        processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            service.invalidateCache()
            throw IllegalArgumentException(
                "Expected full block (IndexingResult.BlockResult) but got " +
                    "${entry::class.simpleName ?: "non-block result"}; " +
                    "full block is required for token reward processing"
            )
        }
        val updated = service.processBlock(entry.block, entry.callResults())
        if (updated.isNotEmpty()) service.save(updated)
    }

    /** Drops the service's cycle and tracker caches so the next block reloads rolled-back rows. */
    override fun resetProcessingState() {
        super.resetProcessingState()
        service.invalidateCache()
    }
}
