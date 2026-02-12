package org.vechain.indexer.stargate.tokenReward

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository

@Profile("stargate", "token-reward")
interface TokenRewardRepository : BaseIndexedRepository<TokenReward, String> {
    @Query("{ 'validator': ?0, 'rewardPeriod': ?1, 'cycle': ?2 }")
    fun findAllByValidatorAndRewardPeriodAndCycle(
        validator: String,
        rewardPeriod: RewardPeriod,
        cycle: Long,
    ): List<TokenReward>

    @Query("{ 'tokenId': ?0, 'rewardPeriod': { '\$in': ?1 }, 'validator': ?2 }")
    fun findByTokenIdAndRewardPeriodInAndValidator(
        tokenId: String,
        rewardPeriod: List<RewardPeriod>,
        validator: String,
        pageable: Pageable,
    ): Slice<TokenReward>

    @Query("{ 'tokenId': ?0, 'rewardPeriod': { '\$in': ?1 } }")
    fun findByTokenIdAndRewardPeriodIn(
        tokenId: String,
        rewardPeriod: List<RewardPeriod>,
        pageable: Pageable,
    ): Slice<TokenReward>
}
