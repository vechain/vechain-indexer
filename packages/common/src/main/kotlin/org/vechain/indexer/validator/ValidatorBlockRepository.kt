package org.vechain.indexer.validator

import org.vechain.indexer.postgres.PostgresIndexedRepository

interface ValidatorBlockRepository : PostgresIndexedRepository {
    fun saveAll(records: List<ValidatorBlock>)

    fun findById(id: String): ValidatorBlock?

    // Finds the latest hourly block per validator and status -> VALIDATED only
    fun findLatestHourly(): List<ValidatorLatestBlockResult>

    // Finds latest daily blocks per validator and status -> VALIDATED only
    fun findLatestDaily(): List<ValidatorLatestBlockResult>

    // Finds latest weekly blocks per validator and status -> VALIDATED only
    fun findLatestWeekly(): List<ValidatorLatestBlockResult>

    // Finds latest monthly blocks per validator and status -> VALIDATED only
    fun findLatestMonthly(): List<ValidatorLatestBlockResult>

    fun findAllInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    fun findHourlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    fun findDailyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    fun findWeeklyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    fun findMonthlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
        validator: String,
    ): List<ValidatorBlock>

    fun findLatestMissed(): List<ValidatorBlock>

    fun findMissedInRange(validator: String, startBlock: Long, endBlock: Long): List<ValidatorBlock>

    fun findAllMissedInRange(startBlock: Long, endBlock: Long): List<ValidatorBlock>

    /** Finds validator blocks with optional filters. */
    fun findByFilters(
        validator: String?,
        status: BlockStatus?,
        blockNumber: Long?,
        pageable: org.springframework.data.domain.Pageable,
    ): org.springframework.data.domain.Slice<ValidatorBlock>
}
