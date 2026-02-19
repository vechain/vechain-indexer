package org.vechain.indexer.accounts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("accounts", "vet-balance")
@Component
open class VetBalanceProcessor(
    repository: VetBalanceRepository,
    private val service: VetBalanceService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.VET_BALANCE.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.VET_BALANCE.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
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
