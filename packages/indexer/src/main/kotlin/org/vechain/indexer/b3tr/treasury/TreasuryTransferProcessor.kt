package org.vechain.indexer.b3tr.treasury

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("b3tr", "b3tr-treasury")
@Component
open class TreasuryTransferProcessor(
    private val service: TreasuryTransferService,
    repository: TreasuryTransferRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.TREASURY_TRANSFER.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.TREASURY_TRANSFER.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val transfers = service.processEvents(entry.events())

        if (transfers.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(transfers) }
        }
    }
}
