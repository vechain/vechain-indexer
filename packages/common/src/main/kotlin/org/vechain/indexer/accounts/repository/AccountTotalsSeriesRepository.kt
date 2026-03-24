package org.vechain.indexer.accounts.repository

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.repository.Query
import org.vechain.indexer.BaseIndexedRepository
import org.vechain.indexer.accounts.AccountTotalsSeries
import org.vechain.indexer.accounts.AccountTotalsSeriesRecordType

@Profile("accounts", "account-totals-series")
interface AccountTotalsSeriesRepository : BaseIndexedRepository<AccountTotalsSeries, String> {
    @Query(
        value = "{ 'recordType': 'SERIES', 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findAllInTimestampRange(startTimestamp: Long, endTimestamp: Long): List<AccountTotalsSeries>

    @Query(
        value =
            "{ 'recordType': 'SERIES', 'isHourly': true, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findHourlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<AccountTotalsSeries>

    @Query(
        value =
            "{ 'recordType': 'SERIES', 'isDaily': true, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findDailyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<AccountTotalsSeries>

    @Query(
        value =
            "{ 'recordType': 'SERIES', 'isWeekly': true, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findWeeklyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<AccountTotalsSeries>

    @Query(
        value =
            "{ 'recordType': 'SERIES', 'isMonthly': true, 'blockTimestamp': { \$gte: ?0, \$lte: ?1 } }",
        sort = "{ 'blockTimestamp': 1 }",
    )
    fun findMonthlyInTimestampRange(
        startTimestamp: Long,
        endTimestamp: Long,
    ): List<AccountTotalsSeries>

    fun findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
        recordType: AccountTotalsSeriesRecordType,
        blockTimestamp: Long,
    ): AccountTotalsSeries?

    fun findFirstByRecordTypeOrderByBlockTimestampDesc(
        recordType: AccountTotalsSeriesRecordType
    ): AccountTotalsSeries?

    fun findFirstByRecordTypeAndBlockNumberLessThanOrderByBlockNumberDesc(
        recordType: AccountTotalsSeriesRecordType,
        blockNumber: Long,
    ): AccountTotalsSeries?
}
