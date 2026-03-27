package org.vechain.indexer.performance.history

import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.history.HistoryRepository
import org.vechain.indexer.history.HistoryService
import org.vechain.indexer.history.IndexedHistoryEvent
import org.vechain.indexer.nft.NftBlacklistClient
import org.vechain.indexer.performance.DetailedProfiler
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.validator.ValidatorDelegationService

/**
 * Extended HistoryService that profiles EVERY internal method call This gives you complete
 * visibility into where time is spent
 */
class ProfiledHistoryService(
    repository: HistoryRepository,
    mongoTemplate: MongoTemplate,
    blacklistClient: NftBlacklistClient,
    delegationLifecycleHistoryService:
        org.vechain.indexer.history.DelegationLifecycleHistoryService,
    validatorDelegationService: ValidatorDelegationService,
    private val profiler: DetailedProfiler,
) :
    HistoryService(
        repository,
        mongoTemplate,
        blacklistClient,
        delegationLifecycleHistoryService,
        validatorDelegationService,
    ) {

    private suspend fun <T> timeSuspend(operationName: String, block: suspend () -> T): T {
        profiler.start(operationName)
        return try {
            block()
        } finally {
            profiler.stop(operationName)
        }
    }

    override suspend fun processBlock(
        events: List<IndexedEvent>,
        block: Block,
        callResponses: List<InspectionResult>,
    ): List<IndexedHistoryEvent> {
        return timeSuspend("      HistoryService.processBlock") {
            super.processBlock(events, block, callResponses)
        }
    }

    override fun save(events: List<IndexedHistoryEvent>) {
        profiler.time("      HistoryService.save (MongoDB)") {
            profiler.time("        - repository.saveAll") { super.save(events) }
        }
    }
}
