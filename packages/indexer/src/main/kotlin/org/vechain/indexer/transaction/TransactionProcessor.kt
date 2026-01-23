package org.vechain.indexer.transaction

import kotlin.time.TimeSource
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexerProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.ProcessorMetrics
import org.vechain.indexer.thor.model.BlockIdentifier
import org.vechain.indexer.version.IndexerVersionService

@Profile("transactions")
@Component
open class TransactionProcessor(
    private val transactionService: TransactionService,
    private val transactionRepository: TransactionRepository,
    private val indexerVersionService: IndexerVersionService,
) : IndexerProcessor {

    private val logger = LoggerFactory.getLogger(this::class.java)

    override suspend fun process(entry: IndexingResult) {
        val start = TimeSource.Monotonic.markNow()
        try {
            processEntry(entry)
            ProcessorMetrics.incrementEventsCounter(
                IndexerNames.TRANSACTION,
                entry.events().size.toDouble(),
            )
        } finally {
            ProcessorMetrics.observeProcessingDuration(IndexerNames.TRANSACTION, start.elapsedNow())
        }
    }

    override fun getLastSyncedBlock(): BlockIdentifier? {
        val latestBlock = transactionRepository.getLatestBlockIdentifier()
        val lastProcessedBlock =
            indexerVersionService.getLastProcessedBlock(IndexerNames.TRANSACTION)

        return when {
            latestBlock != null && lastProcessedBlock != null -> {
                if (latestBlock.number <= lastProcessedBlock.number) {
                    lastProcessedBlock
                } else {
                    latestBlock
                }
            }
            latestBlock != null -> latestBlock
            lastProcessedBlock != null -> lastProcessedBlock
            else -> null
        }
    }

    override fun rollback(blockNumber: Long) {
        transactionRepository.deleteAllByBlockNumberGreaterThanEqual(blockNumber)
    }

    private suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
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
