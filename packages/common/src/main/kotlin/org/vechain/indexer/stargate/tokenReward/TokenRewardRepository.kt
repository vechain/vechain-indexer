package org.vechain.indexer.stargate.tokenReward

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "token-reward")
interface TokenRewardRepository : BaseIndexedRepository<TokenReward, String> {
    fun findAllByValidatorAndRewardPeriodAndCycle(
        validator: String,
        rewardPeriod: RewardPeriod,
        cycle: Long,
    ): List<TokenReward>

    fun findByTokenIdAndRewardPeriodInAndValidator(
        tokenId: String,
        rewardPeriod: List<RewardPeriod>,
        validator: String,
        pageable: Pageable,
    ): Slice<TokenReward>

    fun findByTokenIdAndRewardPeriodIn(
        tokenId: String,
        rewardPeriod: List<RewardPeriod>,
        pageable: Pageable,
    ): Slice<TokenReward>
}
