package org.vechain.indexer.b3tr.navigator

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@Profile("b3tr", "b3tr-navigator")
@Component
open class NavigatorDelegationProcessor(
    private val service: NavigatorDelegationService,
    repository: NavigatorDelegationRepository,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.NAVIGATOR_DELEGATION.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NAVIGATOR_DELEGATION.COLLECTION,
        processorMetrics = processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val delegations = service.processEvents(entry.events())
        service.save(delegations)
    }
}
