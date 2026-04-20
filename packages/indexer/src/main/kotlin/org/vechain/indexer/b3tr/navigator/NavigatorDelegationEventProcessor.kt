package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("b3tr", "b3tr-navigator", "b3tr-navigator-delegation-event")
@Component
open class NavigatorDelegationEventProcessor(
    private val service: NavigatorDelegationEventService,
    repository: NavigatorDelegationEventRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.NAVIGATOR_DELEGATION_EVENT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NAVIGATOR_DELEGATION_EVENT.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val events = service.processEvents(entry.events())
        service.save(events)
    }
}
