package org.vechain.indexer.b3tr.proposal

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.PostgresIndexerStore
import org.vechain.indexer.PostgresProcessor
import org.vechain.indexer.Status
import org.vechain.indexer.backfill.BackfillCoordinatorFactory
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.utils.BlockDetails

@Profile("b3tr", "b3tr-proposal")
@Component
open class ProposalProcessor(
    private val resultService: ProposalResultService,
    private val commentService: ProposalCommentService,
    private val repository: ProposalWriteRepository,
    private val thorClient: ThorClient,
    state: IndexerStateRepository,
    checkpointProperties: CheckpointProperties,
    horizon: InlineVersioningProperties,
    processorMetrics: ProcessorMetrics,
    backfill: BackfillCoordinatorFactory? = null,
    @Value("\${indexer.version.b3tr-proposal:1}") version: Int = 1,
) :
    PostgresProcessor(
        PostgresIndexerStore(
            IndexerNames.PROPOSAL.COLLECTION,
            repository,
            state,
            checkpointProperties,
            horizon,
        ),
        IndexerNames.PROPOSAL.NAME,
        version,
        processorMetrics,
        backfill?.create(ProposalIndexes.SET),
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        val results = resultService.processEvents(entry.events())
        val comments = commentService.processEvents(entry.events())
        val refreshed =
            if (entry.status == Status.FULLY_SYNCED) {
                refreshStates(entry.latestBlockNumber(), results)
            } else {
                emptyList()
            }
        if (results.isNotEmpty() || comments.isNotEmpty() || refreshed.isNotEmpty()) {
            repository.save(results + refreshed, comments)
        }
    }

    // The state is read at the entry's own end block, so nothing is written past the checkpoint.
    private suspend fun refreshStates(
        blockNumber: Long,
        pending: List<ProposalResult>,
    ): List<ProposalResult> {
        val block = thorClient.getBlockUnexpanded(BlockRevision.Number(blockNumber))
        return resultService.refreshStates(
            BlockDetails(block.id, block.number, block.timestamp),
            pending,
        )
    }
}
