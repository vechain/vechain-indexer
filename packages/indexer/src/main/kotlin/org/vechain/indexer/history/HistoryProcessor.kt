package org.vechain.indexer.history

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BasePostgresProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("history")
@Component
open class HistoryProcessor(
    repository: HistoryRepository,
    private val historyService: HistoryService,
    indexerVersionService: IndexerVersionService,
) :
    BasePostgresProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.HISTORY,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
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
            withContext(Dispatchers.IO) { historyService.save(records) }
        }

        if (blacklistEvents.isNotEmpty()) {
            withContext(Dispatchers.IO) { historyService.processBlacklistEvents(blacklistEvents) }
        }
    }
}
