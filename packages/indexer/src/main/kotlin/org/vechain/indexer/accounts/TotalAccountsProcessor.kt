package org.vechain.indexer.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.TotalAccountsRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("accounts", "total-accounts")
@Component
open class TotalAccountsProcessor(
    repository: TotalAccountsRepository,
    private val service: TotalAccountsService,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.TOTAL_ACCOUNTS_INDEXER,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        val (updated, existing) = service.processBlock(entry.block, entry.callResults())

        if (updated.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
