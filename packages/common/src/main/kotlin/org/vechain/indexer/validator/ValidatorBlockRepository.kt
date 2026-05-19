package org.vechain.indexer.validator

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Aggregation
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository

@Profile("validator")
interface ValidatorBlockRepository : BaseIndexedRepository<ValidatorBlock, String> {

    // Finds the latest hourly block per validator and status -> VALIDATED only
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { isHourly: true, status: 'VALIDATED' } }",
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
                "{ '\$match': { isWeekly: true, status: 'VALIDATED' } }",
                "{ '\$sort': { validator: 1,blockNumber: -1 } }",
                "{ '\$group': { _id: { validator: '\$validator'}, blockTimestamp: { '\$first': '\$blockTimestamp' } } }",
            ]
    )
    fun findLatestWeekly(): List<ValidatorLatestBlockResult>

    // Finds latest monthly blocks per validator and status -> VALIDATED only
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { isMonthly: true, status: 'VALIDATED' } }",
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
            "{ 'isHourly': true, 'status': 'VALIDATED', 'validator': ?2, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findHourlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'isDaily': true, 'status': 'VALIDATED', 'validator': ?2, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findDailyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'isWeekly': true, 'status': 'VALIDATED', 'validator': ?2, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findWeeklyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    @Query(
        value =
            "{ 'isMonthly': true, 'status': 'VALIDATED', 'validator': ?2, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findMonthlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    fun findFirstByValidatorAndStatusAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
        validator: String,
        status: BlockStatus,
        blockTimestamp: Long,
    ): ValidatorBlock?

    /**
     * Per-validator slot accounting over `[startTimestamp, endTimestamp]` (inclusive, Unix
     * seconds). VALIDATED rows count as proposed blocks, MISSED rows as missed slots. Validators
     * with no rows in the window are absent from the result.
     */
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'blockTimestamp': { '\$gte': ?0, '\$lte': ?1 }, 'status': { '\$in': ['VALIDATED', 'MISSED'] } } }",
                "{ '\$group': { '_id': '\$validator', 'proposedBlocks': { '\$sum': { '\$cond': [{ '\$eq': ['\$status', 'VALIDATED'] }, 1, 0] } }, 'missedSlots': { '\$sum': { '\$cond': [{ '\$eq': ['\$status', 'MISSED'] }, 1, 0] } } } }",
                "{ '\$addFields': { 'missedSlotRatio': { '\$let': { 'vars': { 'scheduled': { '\$add': ['\$proposedBlocks', '\$missedSlots'] } }, 'in': { '\$cond': [{ '\$gt': ['\$\$scheduled', 0] }, { '\$divide': ['\$missedSlots', '\$\$scheduled'] }, 0.0] } } } } }",
                "{ '\$project': { '_id': 0, 'validator': '\$_id', 'proposedBlocks': 1, 'missedSlots': 1, 'missedSlotRatio': 1 } }",
                "{ '\$sort': { 'missedSlotRatio': -1 } }",
            ]
    )
    fun aggregateSlotStatsInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<ValidatorSlotStats>

    /** Same as [aggregateSlotStatsInTimestampRange] but scoped to a single validator. */
    @Aggregation(
        pipeline =
            [
                "{ '\$match': { 'validator': ?2, 'blockTimestamp': { '\$gte': ?0, '\$lte': ?1 }, 'status': { '\$in': ['VALIDATED', 'MISSED'] } } }",
                "{ '\$group': { '_id': '\$validator', 'proposedBlocks': { '\$sum': { '\$cond': [{ '\$eq': ['\$status', 'VALIDATED'] }, 1, 0] } }, 'missedSlots': { '\$sum': { '\$cond': [{ '\$eq': ['\$status', 'MISSED'] }, 1, 0] } } } }",
                "{ '\$addFields': { 'missedSlotRatio': { '\$let': { 'vars': { 'scheduled': { '\$add': ['\$proposedBlocks', '\$missedSlots'] } }, 'in': { '\$cond': [{ '\$gt': ['\$\$scheduled', 0] }, { '\$divide': ['\$missedSlots', '\$\$scheduled'] }, 0.0] } } } } }",
                "{ '\$project': { '_id': 0, 'validator': '\$_id', 'proposedBlocks': 1, 'missedSlots': 1, 'missedSlotRatio': 1 } }",
            ]
    )
    fun aggregateSlotStatsInTimestampRangeForValidator(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorSlotStats>
}
