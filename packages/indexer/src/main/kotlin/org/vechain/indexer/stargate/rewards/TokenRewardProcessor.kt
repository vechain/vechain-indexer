package org.vechain.indexer.stargate.rewards

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("token-reward")
@Component
open class TokenRewardProcessor(
    private val service: TokenRewardService,
    repository: TokenRewardRepository,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.TOKEN_REWARD,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val (updated, existing) = service.processBlock(entry.block, entry.callResults())

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            service.save(updated, existing)
        }
    }
}
