package org.vechain.indexer.history

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("history")
@Component
open class HistoryProcessor(
    repository: HistoryRepository,
    private val historyService: HistoryService,
) : BaseProcessor(repository) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (block == null) {
            throw IllegalArgumentException("Block cannot be null")
        }

        // If no events or transactions, do nothing
        if (matchedEvents.isEmpty() && block.transactions.isEmpty()) {
            return
        }

        historyService.processBlockEvents(matchedEvents, block)
    }
}
