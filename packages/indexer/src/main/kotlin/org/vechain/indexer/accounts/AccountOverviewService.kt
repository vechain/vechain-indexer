package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.saveVersionedDocuments
import org.vechain.indexer.thor.model.Block

@Profile("accounts", "account-overview")
@Service
open class AccountOverviewService(
    private val repository: AccountOverviewRepository,
    private val archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
    private val accountOverviewPruner: TargetedPruner<AccountOverview, AccountOverviewArchive>,
) {

    open fun processBlock(block: Block): Pair<List<AccountOverview>, List<AccountOverview>> {
        // TODO: Implement account overview processing logic
        return Pair(emptyList(), emptyList())
    }

    @Transactional
    open fun save(updated: List<AccountOverview>, existing: List<AccountOverview>) {
        saveVersionedDocuments(
            updated = updated,
            existing = existing,
            repository = repository,
            archiveService = archiveService,
            pruner = accountOverviewPruner,
        )
    }
}
