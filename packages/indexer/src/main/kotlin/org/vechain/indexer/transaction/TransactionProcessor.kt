package org.vechain.indexer.transaction

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("transactions")
@Component
open class TransactionProcessor(
    private val transactionService: TransactionService,
    repository: TransactionRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.TRANSACTION.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.TRANSACTION.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException("Block must be a normal block.")
        }
        val unknownEvents = entry.events().filter { it.address == null }
        if (unknownEvents.isNotEmpty()) {
            logger.warn(
                "⛔️Unknown events found: ${unknownEvents.joinToString(", ") { it.eventType }}"
            )
        }

        if (entry.block.transactions.isNotEmpty()) {
            transactionService.processBlockTransactions(events = entry.events, block = entry.block)
        }
    }
}
