package org.vechain.indexer.performance.accounts

import org.vechain.indexer.accounts.TotalAccounts
import org.vechain.indexer.accounts.TotalAccountsService
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult

/**
 * Extended AccountsService that profiles EVERY internal method call Tracks performance of:
 * - processBlock (main processing)
 * - save (MongoDB writes)
 * - getNewAccounts (identify new accounts)
 * - updateAccountsInfo (update tracking statistics)
 * - applyRolloverLogic (rollover calculations)
 * - createPeriodAccounts (period account creation)
 */
class ProfiledTotalAccountsService(
    repository: TotalAccountsRepository,
    archiveService: ArchiveService<TotalAccounts>,
    private val profiler: DetailedProfiler,
) : TotalAccountsService(repository, archiveService) {

    override fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<TotalAccounts>, TotalAccounts> {
        return profiler.time("      AccountsService.processBlock") {
            // Get new accounts from block
            val accounts =
                profiler.time("        - getNewAccounts") { getNewAccountsInternal(block) }

            // Update account info for each delegation and handle period rollovers
            profiler.time("        - updateAccountsInfo") {
                updateAccountsInfoInternal(
                    block = block,
                    newAccounts = accounts.second,
                    totalAccountsTracker = accounts.first,
                )
            }
        }
    }

    override fun save(totalAccountsInfo: List<TotalAccounts>, archive: TotalAccounts) {
        profiler.time("      AccountsService.save (MongoDB)") {
            super.save(totalAccountsInfo, archive)
        }
    }

    // Private method accessors using reflection
    private fun getNewAccountsInternal(block: Block): Pair<TotalAccounts, List<TotalAccounts>> {
        val method =
            TotalAccountsService::class.java.getDeclaredMethod("getNewAccounts", Block::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block) as Pair<TotalAccounts, List<TotalAccounts>>
    }

    private fun updateAccountsInfoInternal(
        block: Block,
        newAccounts: List<TotalAccounts>,
        totalAccountsTracker: TotalAccounts,
    ): Pair<List<TotalAccounts>, TotalAccounts> {
        val method =
            TotalAccountsService::class
                .java
                .getDeclaredMethod(
                    "updateAccountsInfo",
                    Block::class.java,
                    List::class.java,
                    TotalAccounts::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, newAccounts, totalAccountsTracker)
            as Pair<List<TotalAccounts>, TotalAccounts>
    }
}
