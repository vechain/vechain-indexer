package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.stargate.TimeSeriesRepo

@Profile("validator", "validator-reward")
interface ValidatorBlockRepository :
    BaseIndexedRepository<ValidatorBlock, Long>, TimeSeriesRepo<ValidatorBlock> {
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

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0, 'status': ?1, 'blockTimestamp': { '\$gte': ?2, '\$lte': ?3 } } }",
                "{ '\$sort': { 'blockTimestamp': 1 } }",
            ]
    )
    fun findByValidatorAndStatusAndBlockTimestampBetween(
        validator: String,
        status: BlockStatus,
        after: Long,
        before: Long,
    ): List<ValidatorBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0,  'status': ?1, 'blockTimestamp': { '\$lte': ?2 }, } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestByValidatorAndStatusBeforeOrAtBlockTimestamp(
        validator: String,
        status: BlockStatus,
        blockTimestamp: Long,
    ): ValidatorBlock?

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': { '\$in': ?0 }, 'status': ?1 } }",
                "{ '\$sort': { 'validator': 1, 'blockNumber': -1 } }",
                "{ '\$group': { '_id': '\$validator', 'latest': { '\$first': '\$\$ROOT' } } }",
            ]
    )
    fun findLatestByValidatorsAndStatus(
        validators: List<String>,
        status: BlockStatus,
    ): List<ValidatorBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0, 'status': ?1 } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestByValidatorAndStatus(validator: String, status: BlockStatus): ValidatorBlock?
}
