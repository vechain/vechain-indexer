package org.vechain.indexer.performance.stargateRewards

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.DelegationV2Repository
import org.vechain.indexer.validator.ValidatorV2Repository

/**
 * Thin profiling wrapper around [TokenRewardService] for the performance test harness. Captures
 * end-to-end timings on `processBlock` and `save`. The previous per-phase breakdown depended on the
 * V1 aggregator decode pipeline; with V2 the per-block path is short enough that top-level timings
 * are sufficient.
 */
class ProfiledTokenRewardService(
    repository: TokenRewardRepository,
    mongoTemplate: MongoTemplate,
    inlineVersioningProperties: InlineVersioningProperties,
    validatorV2Repository: ValidatorV2Repository,
    delegationV2Repository: DelegationV2Repository,
    thorClient: ThorClient,
    private val profiler: DetailedProfiler,
) :
    TokenRewardService(
        repository,
        mongoTemplate,
        inlineVersioningProperties,
        validatorV2Repository,
        delegationV2Repository,
        thorClient,
    ) {

    override suspend fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<TokenReward>, List<TokenReward>> =
        profiler.time("      TokenRewardService.processBlock") {
            super.processBlock(block, callResponses)
        }

    override fun save(rewards: List<TokenReward>, archive: List<TokenReward>) {
        profiler.time("      TokenRewardService.save (MongoDB)") { super.save(rewards, archive) }
    }
}
