package org.vechain.indexer.transfer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("transfers")
@Component
open class TransferProcessor(
    private val service: TransferService,
    repository: TransferWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.transfers:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.TRANSFER.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.TRANSFER.NAME,
        version,
        processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val update = service.processEvents(entry.events())
        if (update.transfers.isNotEmpty()) service.save(update)
    }
}
