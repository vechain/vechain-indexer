package org.vechain.indexer.accounts

import java.math.BigInteger
import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.NumberUtils.hexToBigInteger

@Profile("accounts", "total-accounts")
@Service
open class TotalAccountsService(
    private val repository: TotalAccountsRepository,
    private val archiveService: ArchiveService<TotalAccounts, TotalAccountsArchive>,
) {
    val ONE_VET: BigInteger = BigInteger.TEN.pow(18)

    /**
     * @param block The current Thor block being processed.
     * @param callResponses The list of call inspection results (unused in current implementation).
     * @return A pair of (List<Accounts> to save, global tracker Account "ALL").
     * @notice Processes a single block to identify new accounts and update global tracking info.
     * @dev Retrieves new accounts from the block, updates totals, and prepares records for
     *   persistence.
     */
    open fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<TotalAccounts>, TotalAccounts> {
        // Get new accounts from block
        val accounts = getNewAccounts(block)

        // Update reward info for each delegation and handle period rollovers
        return updateAccountsInfo(
            block = block,
            newAccounts = accounts.second,
            totalAccountsTracker = accounts.first,
        )
    }

    /**
     * @param totalAccountsInfo The list of Accounts entities to save.
     * @param archive The current global "ALL" tracker snapshot.
     * @notice Persists new account records and archives in MongoDB.
     * @dev Skips persistence when no new accounts exist.
     */
    @Transactional
    open fun save(totalAccountsInfo: List<TotalAccounts>, archive: TotalAccounts) {
        if (totalAccountsInfo.isEmpty()) return
        repository.saveAll(totalAccountsInfo)
        archiveService.saveAll(listOf(archive))
    }

    /**
     * @param block The current Thor block being processed.
     * @return A pair of (global "ALL" account, list of new accounts created in this block).
     * @notice Retrieves new accounts appearing in a given block.
     * @dev Compares transaction signers and gas payers to existing accounts in the repository.
     *   Ensures the global account "ALL" is always returned (creates it if missing).
     */
    fun getNewAccounts(block: Block): Pair<TotalAccounts, List<TotalAccounts>> {
        val txSigners = block.transactions.map { it.origin }.toSet()
        val gasPayers = block.transactions.map { it.gasPayer }.toSet()
        val vetHolders =
            block.transactions
                .flatMap { it.clauses }
                .filter { it.value.hexToBigInteger() > ONE_VET }
                .mapNotNull { it.to?.lowercase() }
                .toSet()

        val accounts = txSigners + gasPayers + vetHolders

        // Fetch existing accounts, including the "ALL" one
        val existingAccounts = repository.findAllById(accounts + "ALL").toList()

        // Identify only the new ones
        val newAccountIds = accounts.filterNot { acc -> existingAccounts.any { it.id == acc } }

        // Map new ones to entities
        val newAccountEntities =
            newAccountIds.map {
                TotalAccounts(
                    id = it,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = 0,
                )
            }

        // Add "ALL" if it exists, otherwise create it for tracking
        val allAccount =
            existingAccounts.firstOrNull { it.id == "ALL" }
                ?: TotalAccounts(
                    id = "ALL",
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = 0,
                    total = 0L,
                    timeFrame = TimeFrame.ALL,
                )

        // Return ALL and new accounts
        return Pair(allAccount, newAccountEntities)
    }

    /**
     * @param block The current Thor block.
     * @param newAccounts The new accounts discovered in this block.
     * @param totalAccountsTracker The "ALL" tracker representing cumulative totals.
     * @return A pair of (list of updated Account records, updated "ALL" tracker).
     * @notice Updates account tracking statistics based on new accounts discovered.
     * @dev Rolls over daily, weekly, monthly, and yearly counters when boundaries change. Creates
     *   historical period records for completed intervals.
     */
    fun updateAccountsInfo(
        block: Block,
        newAccounts: List<TotalAccounts>,
        totalAccountsTracker: TotalAccounts,
    ): Pair<List<TotalAccounts>, TotalAccounts> {
        val totalAccountsTracking = mutableListOf<TotalAccounts>()

        totalAccountsTracking.addAll(newAccounts)

        val newAccountsCount = newAccounts.count().toLong()

        // Apply rollover logic to update all period totals
        val (daily, weekly, monthly, yearly) =
            applyRolloverLogic(
                block = block,
                newAccountsCount = newAccountsCount,
                totalAccountsTracker = totalAccountsTracker,
                totalAccountsTracking = totalAccountsTracking,
            )

        // Update main tracker with latest block and total info
        val updatedTracker =
            totalAccountsTracker.copy(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                total = totalAccountsTracker.total?.plus(newAccountsCount),
                dayTotal = daily,
                weekTotal = weekly,
                monthTotal = monthly,
                yearTotal = yearly,
                dayOfMonth =
                    Instant.ofEpochSecond(block.timestamp)
                        .atZone(ZoneOffset.UTC)
                        .dayOfMonth
                        .toLong(),
                weekOfYear =
                    Instant.ofEpochSecond(block.timestamp)
                        .atZone(ZoneOffset.UTC)
                        .toLocalDate()
                        .get(WeekFields.ISO.weekOfYear())
                        .toLong(),
                month =
                    Instant.ofEpochSecond(block.timestamp)
                        .atZone(ZoneOffset.UTC)
                        .monthValue
                        .toLong(),
                year = Instant.ofEpochSecond(block.timestamp).atZone(ZoneOffset.UTC).year.toLong(),
                version = totalAccountsTracker.version + 1,
            )

        totalAccountsTracking.add(updatedTracker)
        return totalAccountsTracking to totalAccountsTracker
    }

    /**
     * @param block The current Thor block.
     * @param newAccountsCount Number of new accounts detected in this block.
     * @param totalAccountsTracker The "ALL" tracker containing existing totals and period info.
     * @param totalAccountsTracking The mutable list collecting new and updated Accounts entities.
     * @return A Quadruple (daily, weekly, monthly, yearly) representing updated totals after
     *   rollover.
     * @notice Applies rollover logic to determine when to archive previous period totals.
     * @dev Checks day, week, month, and year boundaries and creates period snapshots when crossed.
     */
    private fun applyRolloverLogic(
        block: Block,
        newAccountsCount: Long,
        totalAccountsTracker: TotalAccounts,
        totalAccountsTracking: MutableList<TotalAccounts>,
    ): Quadruple<Long, Long, Long, Long> {
        val blockDateTime = Instant.ofEpochSecond(block.timestamp).atZone(ZoneOffset.UTC)
        val blockDate = blockDateTime.toLocalDate()

        val blockDay = blockDate.dayOfMonth.toLong()
        val blockWeek = blockDate.get(WeekFields.ISO.weekOfYear()).toLong()
        val blockMonth = blockDate.monthValue.toLong()
        val blockYear = blockDate.year.toLong()

        var daily = totalAccountsTracker.dayTotal ?: 0L
        var weekly = totalAccountsTracker.weekTotal ?: 0L
        var monthly = totalAccountsTracker.monthTotal ?: 0L
        var yearly = totalAccountsTracker.yearTotal ?: 0L

        fun rollover(condition: Boolean, timeFrame: TimeFrame, oldId: String, total: Long): Long =
            if (condition && total > 0L) {
                totalAccountsTracking.add(
                    createPeriodAccounts(
                        id = oldId,
                        period = timeFrame,
                        total = total,
                        mainTracker = totalAccountsTracker,
                    )
                )
                newAccountsCount
            } else {
                total + newAccountsCount
            }

        // Daily rollover
        daily =
            rollover(
                totalAccountsTracker.dayOfMonth != blockDay ||
                    totalAccountsTracker.month != blockMonth ||
                    totalAccountsTracker.year != blockYear,
                TimeFrame.DAY,
                "${totalAccountsTracker.id}-day-${totalAccountsTracker.year}-${totalAccountsTracker.month}-${totalAccountsTracker.dayOfMonth}",
                daily,
            )

        // Weekly rollover
        weekly =
            rollover(
                totalAccountsTracker.weekOfYear != blockWeek ||
                    totalAccountsTracker.year != blockYear,
                TimeFrame.WEEK,
                "${totalAccountsTracker.id}-week-${totalAccountsTracker.year}-${totalAccountsTracker.weekOfYear}",
                weekly,
            )

        // Monthly rollover
        monthly =
            rollover(
                totalAccountsTracker.month != blockMonth || totalAccountsTracker.year != blockYear,
                TimeFrame.MONTH,
                "${totalAccountsTracker.id}-month-${totalAccountsTracker.month}-${totalAccountsTracker.year}",
                monthly,
            )

        // Yearly rollover
        yearly =
            rollover(
                totalAccountsTracker.year != blockYear,
                TimeFrame.YEAR,
                "${totalAccountsTracker.id}-year-${totalAccountsTracker.year}",
                yearly,
            )

        return Quadruple(daily, weekly, monthly, yearly)
    }

    /**
     * @notice Utility data holder for returning four related Long values (daily, weekly, monthly,
     *   yearly).
     */
    data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    /**
     * @param id The unique identifier for this period.
     * @param period The time frame type (DAY, WEEK, MONTH, YEAR).
     * @param total The total accounts count for the completed period.
     * @param mainTracker The global tracker ("ALL") providing context fields.
     * @return A new Accounts entity for the archived period.
     * @notice Creates a new Accounts record representing a completed period.
     * @dev Used to snapshot daily, weekly, monthly, and yearly totals.
     */
    fun createPeriodAccounts(
        id: String,
        period: TimeFrame,
        total: Long,
        mainTracker: TotalAccounts,
    ): TotalAccounts =
        TotalAccounts(
            id = id,
            blockId = mainTracker.blockId,
            blockNumber = mainTracker.blockNumber,
            blockTimestamp = mainTracker.blockTimestamp,
            total = total,
            timeFrame = period,
            dayOfMonth = mainTracker.dayOfMonth,
            weekOfYear = mainTracker.weekOfYear,
            month = mainTracker.month,
            year = mainTracker.year,
            version = 0,
        )
}
