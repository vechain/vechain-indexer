package org.vechain.indexer.b3tr.relayer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.b3tr.relayer.repository.AutoVotingToggleRepository
import org.vechain.indexer.b3tr.round.B3trRoundService
import org.vechain.indexer.b3tr.round.BaseRoundAwareStatefulProcessor
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.IndexedEvent

@Component
@Profile("b3tr", "b3tr-auto-voting-toggles")
open class AutoVotingToggleProcessor(
    repository: AutoVotingToggleRepository,
    mongoTemplate: MongoTemplate,
    private val service: AutoVotingToggleService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
    b3trRoundService: B3trRoundService,
) :
    BaseRoundAwareStatefulProcessor<AutoVotingToggle>(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.AUTO_VOTING_TOGGLE.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.AUTO_VOTING_TOGGLE.COLLECTION,
        processorMetrics = processorMetrics,
        b3trRoundService = b3trRoundService,
    ) {

    override fun processSlice(
        events: List<IndexedEvent>,
        roundId: Int,
    ): Pair<List<AutoVotingToggle>, List<AutoVotingToggle>> = service.processEvents(events, roundId)

    override fun save(updated: List<AutoVotingToggle>, existing: List<AutoVotingToggle>) {
        service.save(updated, existing)
    }
}
