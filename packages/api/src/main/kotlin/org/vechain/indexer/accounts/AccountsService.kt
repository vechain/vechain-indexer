package org.vechain.indexer.accounts

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.accounts.repository.AccountTotalsSeriesRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.utils.IdUtils
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.utils.TimeValidationUtils

/**
 * @notice Service handling reward aggregation and normalization for accounts.
 * @dev Ensures that period-based queries always include the "ALL" document, and normalizes it to
 *   reflect the requested time frame when necessary.
 */
@Profile("accounts")
@Service
open class AccountsService(
    private val accountOverviewRepository: AccountOverviewRepository,
    private val accountTotalsSeriesRepository: AccountTotalsSeriesRepository,
) {
    @Autowired(required = false)
    private var vthoClaimedByAccountRepository: VthoClaimedByAccountRepository? = null

    fun getTotalSeries(startTimestamp: Long, endTimestamp: Long): List<AccountTotalsSeries> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )

        return when (TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)) {
            TimeSeriesResolution.RAW ->
                accountTotalsSeriesRepository.findAllInTimestampRange(startTimestamp, endTimestamp)
            TimeSeriesResolution.HOURLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    accountTotalsSeriesRepository::findHourlyInTimestampRange,
                    { timestamp ->
                        accountTotalsSeriesRepository
                            .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                                AccountTotalsSeriesRecordType.SERIES,
                                timestamp,
                            )
                    },
                )
            TimeSeriesResolution.DAILY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    accountTotalsSeriesRepository::findDailyInTimestampRange,
                    { timestamp ->
                        accountTotalsSeriesRepository
                            .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                                AccountTotalsSeriesRecordType.SERIES,
                                timestamp,
                            )
                    },
                )
            TimeSeriesResolution.WEEKLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    accountTotalsSeriesRepository::findWeeklyInTimestampRange,
                    { timestamp ->
                        accountTotalsSeriesRepository
                            .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                                AccountTotalsSeriesRecordType.SERIES,
                                timestamp,
                            )
                    },
                )
            TimeSeriesResolution.MONTHLY ->
                TimeSeriesUtils.getBookendedRecords(
                    startTimestamp,
                    endTimestamp,
                    accountTotalsSeriesRepository::findMonthlyInTimestampRange,
                    { timestamp ->
                        accountTotalsSeriesRepository
                            .findFirstByRecordTypeAndBlockTimestampLessThanEqualOrderByBlockTimestampDesc(
                                AccountTotalsSeriesRecordType.SERIES,
                                timestamp,
                            )
                    },
                )
        }
    }

    fun getTotalAccountsLatest(): Long? =
        accountTotalsSeriesRepository
            .findFirstByRecordTypeOrderByBlockTimestampDesc(AccountTotalsSeriesRecordType.SERIES)
            ?.totalAccounts

    fun getOverview(address: Address): AccountOverview? =
        accountOverviewRepository.findByIdOrNull(address.value)

    /**
     * Get account overview with enriched VTHO earnings data. Joins AccountOverview with Stargate
     * VTHO claimed data and computes total VTHO earned.
     *
     * @param address The account address
     * @return AccountOverviewResponse with all VTHO earned fields, or null if not found
     */
    fun getOverviewWithVthoEarnings(address: Address): AccountOverviewResponse? {
        val overview = accountOverviewRepository.findByIdOrNull(address.value) ?: return null

        // Account-level Stargate totals are already stored as a dedicated _id entry.
        val stargateVthoClaimed =
            vthoClaimedByAccountRepository
                ?.findById(IdUtils.generateId(HexUtils.normalise(address.value)))
                ?.map { it.total }
                ?.orElse(BigInteger.ZERO) ?: BigInteger.ZERO

        return AccountOverviewResponse.from(overview, stargateVthoClaimed)
    }
}
