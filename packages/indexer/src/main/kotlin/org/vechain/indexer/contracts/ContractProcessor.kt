package org.vechain.indexer.contracts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.contracts.repository.ContractRepository
import org.vechain.indexer.version.IndexerVersionService

@Profile("contracts", "contract")
@Component
open class ContractProcessor(
    private val service: ContractService,
    repository: ContractRepository,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.CONTRACTS_INDEXER,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }
        val (updated, existing) = service.processBlock(entry.events())

        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
