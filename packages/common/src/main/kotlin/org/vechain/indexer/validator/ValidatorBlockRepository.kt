package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository

@Profile("validator", "validator-reward")
interface ValidatorBlockRepository : BaseIndexedRepository<ValidatorBlock, String> {
    // Finds the latest hourly block per validator and status -> VALIDATED only
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { isDaily: true, status: 'VALIDATED' } }",
                "{ '\$sort': { validator: 1, blockNumber: -1 } }",
                "{ '\$group': { _id: { validator: '\$validator'}, blockTimestamp: { '\$first': '\$blockTimestamp' } } }",
            ]
    )
    fun findLatestHourly(): List<ValidatorLatestBlockResult>

    // Finds latest daily blocks per validator and status -> VALIDATED only
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { isDaily: true, status: 'VALIDATED' } }",
                "{ '\$sort': { validator: 1, blockNumber: -1 } }",
                "{ '\$group': { _id: { validator: '\$validator' }, blockTimestamp: { '\$first': '\$blockTimestamp' } } }",
            ]
    )
    fun findLatestDaily(): List<ValidatorLatestBlockResult>

    // Finds latest weekly blocks per validator and status -> VALIDATED only
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { isDaily: true, status: 'VALIDATED' } }",
                "{ '\$sort': { validator: 1,blockNumber: -1 } }",
                "{ '\$group': { _id: { validator: '\$validator'}, blockTimestamp: { '\$first': '\$blockTimestamp' } } }",
            ]
    )
    fun findLatestWeekly(): List<ValidatorLatestBlockResult>

    // Finds latest monthly blocks per validator and status -> VALIDATED only
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { isDaily: true, status: 'VALIDATED' } }",
                "{ '\$sort': { validator: 1, blockNumber: -1 } }",
                "{ '\$group': { _id: { validator: '\$validator' }, blockTimestamp: { '\$first': '\$blockTimestamp' } } }",
            ]
    )
    fun findLatestMonthly(): List<ValidatorLatestBlockResult>

    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, 'status': 'VALIDATED', 'validator': ?2 }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findAllInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, 'status': 'VALIDATED', 'validator': ?2, \$or: [{ 'isHourly': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findHourlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, 'status': 'VALIDATED', 'validator': ?2, \$or: [{ 'isDaily': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findDailyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, 'status': 'VALIDATED', 'validator': ?2, \$or: [{ 'isWeekly': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findWeeklyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'blockTimestamp': { \$gte: ?0, \$lte: ?1 }, 'status': 'VALIDATED', 'validator': ?2, \$or: [{ 'isMonthly': true }, { 'blockTimestamp': ?0 }, { 'blockTimestamp': ?1 }] }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findMonthlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query("{ 'status': 'MISSED', 'blocksOffline': null }")
    fun findLatestMissed(): List<ValidatorBlock>
}
