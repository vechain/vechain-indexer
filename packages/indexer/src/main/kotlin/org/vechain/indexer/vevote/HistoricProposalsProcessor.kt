package org.vechain.indexer.vevote

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("vevote", "vevote-historic")
@Component
open class HistoricProposalsProcessor(
    private val proposalsService: HistoricProposalsService,
    private val voteService: HistoricProposalsVoteService,
    private val repository: HistoricProposalsWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.vevote-historic:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.HISTORIC_PROPOSALS.COLLECTION,
            repository,
            state,
            checkpointProperties,
        ),
        IndexerNames.HISTORIC_PROPOSALS.NAME,
        version,
        processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val (votes, proposalEvents) = entry.events().partition { it.eventType == "NewVote" }
        val update = proposalsService.processEvents(proposalEvents)
        repository.save(update.proposals, update.descriptions, voteService.processVotes(votes))
    }
}
