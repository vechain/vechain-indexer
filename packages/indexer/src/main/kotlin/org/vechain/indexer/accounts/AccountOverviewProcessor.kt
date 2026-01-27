package org.vechain.indexer.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.AccountOverviewRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "account-overview")
@Component
open class AccountOverviewProcessor(
    repository: AccountOverviewRepository,
    private val service: AccountOverviewService,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.ACCOUNT_OVERVIEW_INDEXER,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val (updated, existing) = service.processBlock(entry.block, entry.events)

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
