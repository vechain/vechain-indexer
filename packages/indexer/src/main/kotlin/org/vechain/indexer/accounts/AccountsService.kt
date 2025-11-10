package org.vechain.indexer.accounts

import java.time.Instant
import java.time.ZoneOffset
import java.time.temporal.WeekFields
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult

@Profile("accounts")
@Service
open class AccountsService(
    private val repository: AccountsRepository,
    private val archiveService: ArchiveService<Accounts, AccountsArchive>,
) {
    open fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<Accounts>, Accounts> {
        // Get new accounts from block
        val accounts = getNewAccounts(block)

        // Update reward info for each delegation and handle period rollovers
        return updateAccountsInfo(
            block = block,
            newAccountsCount = accounts.second.size.toLong(),
            accountsTracker = accounts.first,
        )
    }

    /** @notice Persist a batch of accounts records to MongoDB. */
    @Transactional
    open fun save(accountsInfo: List<Accounts>, archive: Accounts) {
        if (accountsInfo.isEmpty()) return
        repository.saveAll(accountsInfo)

        archiveService.saveAll(listOf(archive))
    }

    fun getNewAccounts(block: Block): Pair<Accounts, List<Accounts>> {
        val txSigners = block.transactions.map { it.origin }.toSet()
        val gasPayers = block.transactions.map { it.gasPayer }.toSet()
        val accounts = txSigners + gasPayers

        // Fetch existing accounts, including the "ALL" one
        val existingAccounts = repository.findAllById(accounts + "ALL").toList()

        // Identify only the new ones
        val newAccountIds = accounts.filterNot { acc -> existingAccounts.any { it.id == acc } }

        // Map new ones to entities
        val newAccountEntities =
            newAccountIds.map {
                Accounts(
                    id = it,
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = 0,
                )
            }

        // Add "ALL" if it exists
        val allAccount =
            existingAccounts.firstOrNull { it.id == "ALL" }
                ?: Accounts(
                    id = "ALL",
                    blockId = block.id,
                    blockNumber = block.number,
                    blockTimestamp = block.timestamp,
                    version = 0,
                )

        // Return ALL and new accounts
        return Pair(allAccount, newAccountEntities)
    }

    fun updateAccountsInfo(
        block: Block,
        newAccountsCount: Long,
        accountsTracker: Accounts,
    ): Pair<List<Accounts>, Accounts> {
        val accountsTracking = mutableListOf<Accounts>()
        val archive = mutableListOf<Accounts>()

        val blockDateTime = Instant.ofEpochSecond(block.timestamp).atZone(ZoneOffset.UTC)
        val blockDate = blockDateTime.toLocalDate()

        val blockDay = blockDate.dayOfMonth.toLong()
        val blockWeek = blockDate.get(WeekFields.ISO.weekOfYear()).toLong()
        val blockMonth = blockDate.monthValue.toLong()
        val blockYear = blockDate.year.toLong()

        // convenience values
        var daily = accountsTracker.dayTotal ?: 0L
        var weekly = accountsTracker.weekTotal ?: 0L
        var monthly = accountsTracker.monthTotal ?: 0L
        var yearly = accountsTracker.yearTotal ?: 0L

        // Check rollover for each period
        fun rollover(condition: Boolean, timeFrame: TimeFrame, oldId: String, total: Long): Long =
            if (condition && total > 0L) {
                accountsTracking.add(
                    createPeriodAccounts(
                        id = oldId,
                        period = timeFrame,
                        total = total,
                        mainTracker = accountsTracker,
                    )
                )
                newAccountsCount
            } else {
                total.plus(newAccountsCount)
            }

        // Apply rollover logic
        daily =
            rollover(
                accountsTracker.dayOfMonth != blockDay ||
                    accountsTracker.month != blockMonth ||
                    accountsTracker.year != blockYear,
                TimeFrame.DAY,
                "${accountsTracker.id}-day-${accountsTracker.year}-${accountsTracker.month}-${accountsTracker.dayOfMonth}",
                daily,
            )
        weekly =
            rollover(
                accountsTracker.weekOfYear != blockWeek || accountsTracker.year != blockYear,
                TimeFrame.WEEK,
                "${accountsTracker.id}-week-${accountsTracker.year}-${accountsTracker.weekOfYear}",
                weekly,
            )
        monthly =
            rollover(
                accountsTracker.month != blockMonth || accountsTracker.year != blockYear,
                TimeFrame.MONTH,
                "${accountsTracker.id}-month-${accountsTracker.month}-${accountsTracker.year}",
                monthly,
            )

        yearly =
            rollover(
                accountsTracker.year != blockYear,
                TimeFrame.YEAR,
                "${accountsTracker.id}-year-${accountsTracker.year}",
                yearly,
            )

        // Update main reward tracker
        val updatedTracker =
            accountsTracker.copy(
                blockId = block.id,
                blockNumber = block.number,
                blockTimestamp = block.timestamp,
                total = accountsTracker.total?.plus(newAccountsCount),
                dayTotal = daily,
                weekTotal = weekly,
                monthTotal = monthly,
                yearTotal = yearly,
                dayOfMonth = blockDay,
                weekOfYear = blockWeek,
                month = blockMonth,
                year = blockYear,
                version = accountsTracker.version + 1,
            )

        accountsTracking.add(updatedTracker)
        return accountsTracking to accountsTracker
    }

    fun createPeriodAccounts(
        id: String,
        period: TimeFrame,
        total: Long,
        mainTracker: Accounts,
    ): Accounts =
        Accounts(
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
