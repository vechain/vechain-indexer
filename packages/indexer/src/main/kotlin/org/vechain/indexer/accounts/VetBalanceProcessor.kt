package org.vechain.indexer.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.VetBalanceRepository

@Profile("accounts", "vet-balance")
@Component
open class VetBalanceProcessor(
    repository: VetBalanceRepository,
    private val service: VetBalanceService,
) : BaseProcessor(repository = repository, indexerName = IndexerNames.VET_BALANCE_INDEXER) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }
        val records = service.processEvents(entry.events())

        if (records.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(records) }
        }
    }
}
