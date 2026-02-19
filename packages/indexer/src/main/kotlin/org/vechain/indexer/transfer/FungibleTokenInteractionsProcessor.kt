package org.vechain.indexer.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("transfers")
@Component
open class FungibleTokenInteractionsProcessor(
    private val service: FungibleTokenInteractionsService,
    repository: FungibleTokenInteractionsRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val transferEvents = service.processEvents(entry.events())

        if (transferEvents.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(transferEvents) }
        }
    }
}
