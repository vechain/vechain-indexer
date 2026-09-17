package org.vechain.indexer.b3tr.treasury

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.backfill.BackfillCoordinatorFactory
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("b3tr", "b3tr-treasury")
@Component
open class TreasuryTransferProcessor(
    private val service: TreasuryTransferService,
    repository: TreasuryTransferWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.b3tr-treasury:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.TREASURY_TRANSFER.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.TREASURY_TRANSFER.NAME,
        version,
        processorMetrics,
        backfill?.create(TreasuryTransferIndexes.SET),
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val transfers = service.processEvents(entry.events())
        if (transfers.isNotEmpty()) service.save(transfers)
    }
}
