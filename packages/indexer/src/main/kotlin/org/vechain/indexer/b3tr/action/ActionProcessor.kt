package org.vechain.indexer.b3tr.action

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.config.postgres.PostgresConfig
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("b3tr", "b3tr-actions")
@Component
open class ActionProcessor(
    private val service: ActionSummaryService,
    private val repository: ActionWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.b3tr-action:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.B3TR_ACTION.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.B3TR_ACTION.NAME,
        version,
        processorMetrics,
    ) {
    // Advanced only once the entry is saved, so a failed save is replayed from the same round.
    private var round: Int? = null

    override suspend fun processEntry(entry: IndexingResult) {
        val events = entry.events()
        if (events.isEmpty()) return

        val result =
            service.processEvents(events, round ?: service.roundBefore(events.first().blockNumber))
        if (!result.update.isEmpty()) repository.save(result.update)
        round = result.round
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    override fun rollback(blockNumber: Long) {
        round = null
        super.rollback(blockNumber)
    }
}
