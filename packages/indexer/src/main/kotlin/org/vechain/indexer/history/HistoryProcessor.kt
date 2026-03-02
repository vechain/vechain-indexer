package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("history")
@Component
open class HistoryProcessor(
    repository: HistoryRepository,
    private val historyService: HistoryService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.HISTORY.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.HISTORY.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.BlockResult) {
            throw IllegalArgumentException("Expected IndexingResult.BlockResult (full block result required)")
        }
        // If no events or transactions, do nothing
        if (entry.events().isEmpty() && entry.block.transactions.isEmpty()) {
            return
        }

        // Filter out blacklist and whitelist events and handle them separately
        val (blacklistEvents, historyEvents) =
            entry
                .events()
                .partition({
                    it.eventType == "NFT_Blacklisted" || it.eventType == "NFT_Whitelisted"
                })

        val records = historyService.processEvents(historyEvents, entry.block)

        if (records.isNotEmpty()) {
            historyService.save(records)
        }

        if (blacklistEvents.isNotEmpty()) {
            historyService.processBlacklistEvents(blacklistEvents)
        }
    }
}
