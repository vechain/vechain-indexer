package org.vechain.indexer.performance.history

import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.nft.NftBlacklistClient
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block

/**
 * Extended HistoryService that profiles EVERY internal method call This gives you complete
 * visibility into where time is spent
 */
class ProfiledHistoryService(
    repository: HistoryRepository,
    blacklistClient: NftBlacklistClient,
    private val profiler: DetailedProfiler,
) : HistoryService(repository, blacklistClient) {

    private suspend fun <T> timeSuspend(operationName: String, block: suspend () -> T): T {
        profiler.start(operationName)
        return try {
            block()
        } finally {
            profiler.stop(operationName)
        }
    }

    override suspend fun processEvents(
        events: List<IndexedEvent>,
        block: Block,
    ): List<IndexedHistoryEvent> {
        return timeSuspend("      HistoryService.processEvents") {
            super.processEvents(events, block)
        }
    }

    override fun save(events: List<IndexedHistoryEvent>) {
        profiler.time("      HistoryService.save (PostgreSQL)") {
            profiler.time("        - repository.saveAll") { super.save(events) }
        }
    }
}
