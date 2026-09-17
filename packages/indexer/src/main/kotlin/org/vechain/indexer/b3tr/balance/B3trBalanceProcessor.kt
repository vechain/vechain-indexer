package org.vechain.indexer.b3tr.balance

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
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-balance")
@Component
open class B3trBalanceProcessor(
    private val service: B3trBalanceService,
    repository: B3trBalanceWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.b3tr-balance:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.B3TR_BALANCE.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.B3TR_BALANCE.NAME,
        version,
        processorMetrics,
        backfill?.create(B3trBalanceIndexes.SET),
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry is IndexingResult.BlockResult) {
            val nonRevertedTxIds =
                entry.block.transactions.filter { !it.reverted }.map { it.id }.toSet()
            val confirmedEvents = entry.events.filter { it.txId in nonRevertedTxIds }
            val balances = service.processBlock(entry.block, confirmedEvents)
            if (balances.isNotEmpty()) service.save(balances)
        } else if (entry.events().isNotEmpty()) {
            groupByBlock(entry.events()).forEach { (blockDetails, blockEvents) ->
                val balances = service.processBlock(blockDetails, blockEvents)
                if (balances.isNotEmpty()) service.save(balances)
            }
        }
    }
}
