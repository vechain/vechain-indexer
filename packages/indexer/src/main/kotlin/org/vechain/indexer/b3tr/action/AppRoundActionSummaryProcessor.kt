package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.action.repository.AppRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.b3tr.round.BaseRoundAwareStatefulProcessor
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.IndexedEvent

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-app-round-action-summary")
open class AppRoundActionSummaryProcessor(
    repository: AppRoundActionSummaryRepository,
    mongoTemplate: MongoTemplate,
    private val service: AppRoundActionSummaryService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
    b3trRoundService: B3trRoundService,
) :
    BaseRoundAwareStatefulProcessor<AppRoundActionSummary>(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.APP_ROUND_ACTION_SUMMARY.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.APP_ROUND_ACTION_SUMMARY.COLLECTION,
        processorMetrics = processorMetrics,
        b3trRoundService = b3trRoundService,
    ) {

    override fun processSlice(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Pair<List<AppRoundActionSummary>, List<AppRoundActionSummary>> =
        service.processEvents(events, roundId)

    override fun save(updated: List<AppRoundActionSummary>, existing: List<AppRoundActionSummary>) {
        service.save(updated, existing)
    }
}
