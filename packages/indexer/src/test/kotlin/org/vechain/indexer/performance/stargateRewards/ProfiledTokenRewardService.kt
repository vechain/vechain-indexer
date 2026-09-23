package org.vechain.indexer.performance.stargateRewards

import org.vechain.indexer.Indexer
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.stargate.rewards.TokenRewardService
import org.vechain.indexer.stargate.tokenReward.TokenReward
import org.vechain.indexer.stargate.tokenReward.TokenRewardWriteRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.validator.DelegationReadRepository
import org.vechain.indexer.validator.ValidatorReadRepository

/**
 * Thin profiling wrapper around [TokenRewardService] for the performance test harness. Captures
 * end-to-end timings on `processBlock` and `save`. The previous per-phase breakdown depended on the
 * V1 aggregator decode pipeline; with V2 the per-block path is short enough that top-level timings
 * are sufficient.
 */
class ProfiledTokenRewardService(
    repository: TokenRewardWriteRepository,
    validatorV2Repository: ValidatorReadRepository,
    delegationV2Repository: DelegationReadRepository,
    committedParent: Indexer,
    thorClient: ThorClient,
    stakerAddress: String,
    validatorStartBlock: Long,
    private val profiler: DetailedProfiler,
) :
    TokenRewardService(
        repository,
        validatorV2Repository,
        delegationV2Repository,
        committedParent,
        committedParent,
        thorClient,
        stakerAddress,
        validatorStartBlock,
    ) {

    override suspend fun processBlock(block: Block): List<TokenReward> =
        profiler.time("      TokenRewardService.processBlock") { super.processBlock(block) }

    override fun save(rewards: List<TokenReward>) {
        profiler.time("      TokenRewardService.save (Postgres)") { super.save(rewards) }
    }
}
