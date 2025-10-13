package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.stargate.TimeSeriesRepo

@Profile("validator", "validator-reward")
interface ValidatorRewardRepository :
    BaseIndexedRepository<ValidatorReward, Long>, TimeSeriesRepo<ValidatorReward> {
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockNumber': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockNumber(blockNumber: Long): ValidatorReward?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockTimestamp': { '\$lte': ?0 } } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    override fun findLatestBeforeOrAtBlockTimestamp(blockTimestamp: Long): ValidatorReward?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0 } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestByValidator(validator: String): ValidatorReward?
}
