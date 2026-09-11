package org.vechain.indexer.history

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

@Profile("history")
@Component
open class HistoryProcessor(
    private val historyService: HistoryService,
    repository: HistoryWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.history:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.HISTORY.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.HISTORY.NAME,
        version,
        processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        require(entry is IndexingResult.BlockResult) {
            "Expected IndexingResult.BlockResult (full block result required)"
        }
        val records = historyService.processBlock(entry.events(), entry.block)
        if (records.isNotEmpty()) historyService.save(records)
    }

    @Transactional(
        transactionManager = PostgresConfig.TRANSACTION_MANAGER,
        rollbackFor = [Exception::class],
    )
    override fun rollback(blockNumber: Long) {
        historyService.invalidateDelegationLifecycleState()
        super.rollback(blockNumber)
    }
}
