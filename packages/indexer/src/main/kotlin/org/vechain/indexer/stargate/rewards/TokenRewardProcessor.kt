package org.vechain.indexer.stargate.rewards

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardArchive
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "token-reward")
@Component
open class TokenRewardProcessor(
    private val service: TokenRewardService,
    repository: TokenRewardRepository,
    archiveService: ArchiveService<TokenReward, TokenRewardArchive>,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.TOKEN_REWARD,
    ) {
    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val newRecords = service.processBlock(entry.block, entry.callResults())

        if (newRecords.first.isNotEmpty()) {
            service.save(newRecords.first, newRecords.second)
        }
    }
}
