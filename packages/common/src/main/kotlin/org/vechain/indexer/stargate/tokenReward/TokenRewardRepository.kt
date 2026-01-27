package org.vechain.indexer.stargate.tokenReward

import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.vechain.indexer.postgres.PostgresIndexedRepository

interface TokenRewardRepository : PostgresIndexedRepository {
    fun saveAllVersioned(updated: List<TokenReward>, existing: List<TokenReward>)

    fun findById(id: String): TokenReward?

    fun findAllById(ids: Collection<String>): List<TokenReward>

    fun saveAll(rewards: List<TokenReward>)

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
