package org.vechain.indexer.stargate.tokenReward

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.stargate.timeSeries.TimeSeriesRepo
import org.vechain.indexer.validator.ValidatorBlock

@Profile("validator", "validator-reward")
interface TokenRewardRepository :
    BaseIndexedRepository<TokenReward, String>, TimeSeriesRepo<ValidatorBlock> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): ValidatorBlock?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockTimestamp': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): ValidatorBlock?

    fun findAllByValidatorAndRewardPeriodAndCycle(
        validator: String,
        rewardPeriod: RewardPeriod,
        cycle: Long,
    ): List<TokenReward>
}
