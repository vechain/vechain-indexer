package org.vechain.indexer.b3tr.navigator

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
import org.vechain.indexer.utils.BlockDetails

@Profile("b3tr", "b3tr-navigator")
@Component
open class NavigatorProcessor(
    private val service: NavigatorService,
    private val feeService: NavigatorFeeService,
    private val delegationEventService: NavigatorDelegationEventService,
    private val repository: NavigatorWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.b3tr-navigator:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.NAVIGATOR.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.NAVIGATOR.NAME,
        version,
        processorMetrics,
    ) {

    /** Every block, events or not: an exit deadline passing is not something the chain emits. */
    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected IndexingResult.BlockResult (full block result) but got ${entry::class.simpleName}"
        }
        val block = BlockDetails(entry.block.id, entry.block.number, entry.block.timestamp)
        val events = entry.events()
        val update =
            service
                .processBlock(block, events)
                .copy(
                    delegationEvents = delegationEventService.processEvents(events),
                    fees = feeService.processBlock(block, events),
                )
        if (!update.isEmpty()) repository.save(update)
    }
}
