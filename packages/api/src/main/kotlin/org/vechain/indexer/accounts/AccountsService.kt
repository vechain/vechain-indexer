package org.vechain.indexer.accounts

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.domain.SliceImpl
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByAccountRepository
import org.vechain.indexer.thor.Address

/**
 * @notice Service handling reward aggregation and normalization for accounts.
 * @dev Ensures that period-based queries always include the "ALL" document, and normalizes it to
 *   reflect the requested time frame when necessary.
 */
@Profile("accounts")
@Service
open class AccountsService(
    private val totalAccountsRepository: TotalAccountsRepository,
    private val accountOverviewRepository: AccountOverviewRepository,
) {
    @Autowired(required = false)
    private var vthoClaimedByAccountRepository: VthoClaimedByAccountRepository? = null

    /**
     * @param period The reward period to query. Defaults to ALL if not provided.
     * @param pageable Spring pageable object controlling pagination and sorting.
     * @return A Slice of TokenReward objects representing paginated results.
     * @notice Retrieves account rewards for a given reward period.
     * @dev When querying for a specific period (e.g., DAY), includes the "ALL" document in the
     *   query and normalizes it to match the requested period.
     */
    fun getTotal(period: AccountQueryTimeFrame?, pageable: Pageable): Slice<TotalAccounts> {
        val targetPeriod =
            when (period) {
                AccountQueryTimeFrame.DAY -> TimeFrame.DAY
                AccountQueryTimeFrame.WEEK -> TimeFrame.WEEK
                AccountQueryTimeFrame.MONTH -> TimeFrame.MONTH
                AccountQueryTimeFrame.YEAR -> TimeFrame.YEAR
                AccountQueryTimeFrame.ALL,
                null -> TimeFrame.ALL
            }

        // Always include ALL when querying a specific period (for normalization)
        val periods =
            if (targetPeriod == TimeFrame.ALL) {
                listOf(TimeFrame.ALL)
            } else {
                listOf(targetPeriod, TimeFrame.ALL)
            }

        val slice = totalAccountsRepository.findByTimeFrameIn(periods, pageable)

        // If querying ALL directly or no data exists, return as-is
        if (targetPeriod == TimeFrame.ALL || slice.isEmpty) {
            return slice
        }

        // Determine edge index depending on sort order (for replacing the "ALL" record)
        val isDesc = pageable.sort.getOrderFor("blockTimestamp")?.isDescending ?: true
        val edgeIndex = if (isDesc) 0 else slice.content.lastIndex

        val adjustedContent = slice.content.toMutableList()
        val edgeDoc = adjustedContent[edgeIndex]

        // Replace the "ALL" document with normalized values for the target period
        if (edgeDoc.timeFrame == TimeFrame.ALL) {
            adjustedContent[edgeIndex] = normalizeAllAs(edgeDoc, targetPeriod)
        } else {
            val idx = adjustedContent.indexOfFirst { it.timeFrame == TimeFrame.ALL }
            if (idx >= 0) {
                adjustedContent[idx] = normalizeAllAs(adjustedContent[idx], targetPeriod)
            }
        }

        return SliceImpl(adjustedContent, pageable, slice.hasNext())
    }

    /**
     * @param allDoc The "ALL" TokenReward document.
     * @param target The target TimeFrame (DAY, WEEK, MONTH, etc.).
     * @return A copy of the TokenReward normalized to the target period.
     * @notice Normalizes an "ALL" period TokenReward to mimic a specific time frame.
     * @dev This allows the "ALL" record to reflect the target period’s cumulative rewards.
     */
    private fun normalizeAllAs(allDoc: TotalAccounts, target: TimeFrame): TotalAccounts {
        val normalized =
            when (target) {
                TimeFrame.DAY -> allDoc.dayTotal
                TimeFrame.WEEK -> allDoc.weekTotal
                TimeFrame.MONTH -> allDoc.monthTotal
                TimeFrame.YEAR -> allDoc.yearTotal
                TimeFrame.ALL -> allDoc.total
                else ->
                    throw IllegalArgumentException(
                        "Unsupported time frame for normalization: $target"
                    )
            } ?: 0L

        return allDoc.copy(
            total = normalized,
            timeFrame = target,
            // Hide individual timeframe details in the response
            dayTotal = null,
            weekTotal = null,
            monthTotal = null,
            yearTotal = null,
        )
    }

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

        // Sum up all Stargate VTHO claimed for this account (could be multiple token IDs)
        val stargateVthoClaimed =
            vthoClaimedByAccountRepository?.findByAccount(address.value)?.sumOf { it.total }
                ?: BigInteger.ZERO

        return AccountOverviewResponse.from(overview, stargateVthoClaimed)
    }
}
