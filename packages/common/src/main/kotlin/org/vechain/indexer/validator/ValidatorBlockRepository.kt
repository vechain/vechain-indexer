package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
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

    fun findByValidator(validator: String, pageable: Pageable): Slice<ValidatorBlock>

    fun findAllByOrderByBlockNumberDesc(pageable: Pageable): Slice<ValidatorBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0, 'blockNumber': { '\$lte': ?1 } } }",
                "{ '\$sort': { 'blockNumber': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestByValidatorBeforeOrAtBlockNumber(
        validator: String,
        blockNumber: Long,
        pageable: Pageable,
    ): Slice<ValidatorBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0, 'blockTimestamp': { '\$gte': ?1, '\$lte': ?2 } } }",
                "{ '\$sort': { 'blockTimestamp': 1 } }",
            ]
    )
    fun findByValidatorAndBlockTimestampBetween(
        validator: String,
        after: Long,
        before: Long,
    ): List<ValidatorBlock>

    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?0, 'blockTimestamp': { '\$lte': ?1 } } }",
                "{ '\$sort': { 'blockTimestamp': -1 } }",
                "{ '\$limit': 1 }",
            ]
    )
    fun findLatestByValidatorBeforeOrAtBlockTimestamp(
        validator: String,
        blockTimestamp: Long,
    ): ValidatorBlock?

    fun findByBlockNumber(blockNumber: Long, pageable: Pageable): Slice<ValidatorBlock>
}
