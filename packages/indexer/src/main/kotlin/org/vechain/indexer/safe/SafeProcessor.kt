package org.vechain.indexer.safe

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository

@Profile("safe")
@Component
open class SafeProcessor(
    private val proxyService: SafeProxyService,
    private val membershipService: SafeMembershipService,
    private val txStateService: SafeTxStateService,
    private val proposalService: SafeTxProposalService,
    private val repository: SafeWriteRepository,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    @Value("\${indexer.version.safe:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.SAFE.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.SAFE.NAME,
        version,
        processorMetrics,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return
        val proxies = proxyService.processEvents(entry.events())
        // A Safe is deployed and set up in the same transaction, so this entry's own proxies count.
        val known =
            repository.knownSafes(SafeEventUtils.candidateSafes(entry.events())) +
                proxies.map { it.address }

        val update =
            SafeUpdate(
                proxies = proxies,
                memberships = membershipService.processEvents(entry.events(), known),
                txStates = txStateService.processEvents(entry.events(), known),
                proposals = proposalService.processEvents(entry.events(), known),
            )
        if (!update.isEmpty()) repository.save(update)
    }
}
