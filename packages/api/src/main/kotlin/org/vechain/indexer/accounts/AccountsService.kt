package org.vechain.indexer.accounts

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedReadRepository
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.HexUtils
import org.vechain.indexer.timeseries.TimeSeriesResolution
import org.vechain.indexer.utils.TimeSeriesUtils
import org.vechain.indexer.utils.TimeValidationUtils

@Profile("accounts")
@Service
open class AccountsService(
    private val overviews: AccountOverviewReadRepository,
    private val totals: AccountTotalsReadRepository,
) {
    @Autowired(required = false)
    private var vthoClaimedRepository: VthoClaimedReadRepository? = null

    fun getTotalSeries(startTimestamp: Long, endTimestamp: Long): List<AccountTotalsSeries> {
        TimeValidationUtils.validateTimestamps(
            startTimestamp,
            endTimestamp,
            "startTimestamp",
            "endTimestamp",
        )
        return when (TimeSeriesUtils.selectResolution(endTimestamp - startTimestamp)) {
            TimeSeriesResolution.RAW -> totals.findAllInTimestampRange(startTimestamp, endTimestamp)
            TimeSeriesResolution.HOURLY -> sampled(TimeFrame.HOUR, startTimestamp, endTimestamp)
            TimeSeriesResolution.DAILY -> sampled(TimeFrame.DAY, startTimestamp, endTimestamp)
            TimeSeriesResolution.WEEKLY -> sampled(TimeFrame.WEEK, startTimestamp, endTimestamp)
            TimeSeriesResolution.MONTHLY -> sampled(TimeFrame.MONTH, startTimestamp, endTimestamp)
        }
    }

    private fun sampled(frame: TimeFrame, from: Long, to: Long): List<AccountTotalsSeries> =
        TimeSeriesUtils.getBookendedRecords(
            from,
            to,
            { after, before -> totals.findFrameInTimestampRange(frame, after, before) },
            totals::findLatestAtOrBefore,
        )

    fun getTotalAccountsLatest(): Long? = totals.findLatest()?.totalAccounts

    /** The overview with the Stargate VTHO the account has claimed folded into its earnings. */
    fun getOverviewWithVthoEarnings(address: Address): AccountOverviewResponse? {
        val overview = overviews.findByAddress(address.value) ?: return null
        val stargateVthoClaimed =
            vthoClaimedRepository?.findByAccount(HexUtils.normalise(address.value))?.total
                ?: BigInteger.ZERO
        return AccountOverviewResponse.from(overview, stargateVthoClaimed)
    }
}
