package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("history")
@Component
open class HistoryProcessor(
    repository: HistoryRepository,
    private val historyService: HistoryService,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = "HistoryIndexer",
    ) {

    override fun process(entry: IndexingResult) {
        if (entry !is IndexingResult.Normal) {
            throw IllegalArgumentException("Block cannot be null")
        }
        // If no events or transactions, do nothing
        if (entry.events().isEmpty() && entry.block.transactions.isEmpty()) {
            return
        }

        val records = historyService.processEvents(entry.events(), entry.block)

        if (records.isNotEmpty()) {
            historyService.save(records)
        }
    }
}
