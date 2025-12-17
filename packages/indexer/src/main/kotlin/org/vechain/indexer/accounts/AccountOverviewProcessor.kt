package org.vechain.indexer.accounts

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "account-overview")
@Component
open class AccountOverviewProcessor(
    private val service: AccountOverviewService,
    repository: AccountOverviewRepository,
    archiveService: ArchiveService<AccountOverview, AccountOverviewArchive>,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = archiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.ACCOUNT_OVERVIEW_INDEXER,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val newRecords = service.processBlock(entry.block)

        if (newRecords.first.isNotEmpty() || newRecords.second.isNotEmpty()) {
            service.save(newRecords.first, newRecords.second)
        }
    }
}
