package org.vechain.indexer.performance.accounts

import org.vechain.indexer.accounts.Accounts
import org.vechain.indexer.accounts.AccountsArchive
import org.vechain.indexer.accounts.AccountsRepository
import org.vechain.indexer.accounts.AccountsService
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
class ProfiledAccountsService(
    repository: AccountsRepository,
    archiveService: ArchiveService<Accounts, AccountsArchive>,
    private val profiler: DetailedProfiler,
) : AccountsService(repository, archiveService) {

    override fun processBlock(
        block: Block,
        callResponses: List<InspectionResult>,
    ): Pair<List<Accounts>, Accounts> {
        return profiler.time("      AccountsService.processBlock") {
            // Get new accounts from block
            val accounts =
                profiler.time("        - getNewAccounts") { getNewAccountsInternal(block) }

            // Update account info for each delegation and handle period rollovers
            profiler.time("        - updateAccountsInfo") {
                updateAccountsInfoInternal(
                    block = block,
                    newAccounts = accounts.second,
                    accountsTracker = accounts.first,
                )
            }
        }
    }

    override fun save(accountsInfo: List<Accounts>, archive: Accounts) {
        profiler.time("      AccountsService.save (MongoDB)") { super.save(accountsInfo, archive) }
    }

    // Private method accessors using reflection
    private fun getNewAccountsInternal(block: Block): Pair<Accounts, List<Accounts>> {
        val method =
            AccountsService::class.java.getDeclaredMethod("getNewAccounts", Block::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block) as Pair<Accounts, List<Accounts>>
    }

    private fun updateAccountsInfoInternal(
        block: Block,
        newAccounts: List<Accounts>,
        accountsTracker: Accounts,
    ): Pair<List<Accounts>, Accounts> {
        val method =
            AccountsService::class
                .java
                .getDeclaredMethod(
                    "updateAccountsInfo",
                    Block::class.java,
                    List::class.java,
                    Accounts::class.java,
                )
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(this, block, newAccounts, accountsTracker)
            as Pair<List<Accounts>, Accounts>
    }
}
