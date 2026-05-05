package org.vechain.indexer.b3tr.action

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.action.repository.UserRoundActionSummaryRepository
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.b3tr.round.BaseRoundAwareStatefulProcessor
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.IndexedEvent

@Component
@Profile("b3tr", "b3tr-actions", "b3tr-user-round-action-summary")
open class UserRoundActionSummaryProcessor(
    repository: UserRoundActionSummaryRepository,
    mongoTemplate: MongoTemplate,
    private val service: UserRoundActionSummaryService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
    b3trRoundService: B3trRoundService,
) :
    BaseRoundAwareStatefulProcessor<UserRoundActionSummary>(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.USER_ROUND_ACTION_SUMMARY.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.USER_ROUND_ACTION_SUMMARY.COLLECTION,
        processorMetrics = processorMetrics,
        b3trRoundService = b3trRoundService,
    ) {

    override fun processSlice(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Pair<List<UserRoundActionSummary>, List<UserRoundActionSummary>> =
        service.processEvents(events, roundId)

    override fun save(
        updated: List<UserRoundActionSummary>,
        existing: List<UserRoundActionSummary>,
    ) {
        service.save(updated, existing)
    }
}
