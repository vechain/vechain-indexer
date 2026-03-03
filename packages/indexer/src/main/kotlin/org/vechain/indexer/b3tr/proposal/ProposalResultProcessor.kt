package org.vechain.indexer.b3tr.proposal

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.EventUtils.groupByBlock

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Component
open class ProposalResultProcessor(
    repository: ProposalResultRepository,
    mongoTemplate: MongoTemplate,
    private val service: ProposalResultService,
    checkpointService: CheckpointService,
    processorMetrics: ProcessorMetrics,
) :
    BaseStatefulProcessor(
        repository = repository,
        mongoTemplate = mongoTemplate,
        indexerName = IndexerNames.PROPOSAL_RESULT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.PROPOSAL_RESULT.COLLECTION,
        processorMetrics = processorMetrics,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        val accumulator = VersionedDocumentAccumulator<ProposalResult>(service::findByProposalId)

        if (entry is IndexingResult.BlockResult) {
            val blockDetails =
                BlockDetails(
                    blockId = entry.block.id,
                    blockNumber = entry.block.number,
                    blockTimestamp = entry.block.timestamp,
                )
            accumulator.startBlock()

            // Status updates first — puts updated proposals in accumulator cache
            service.updateStatuses(blockDetails, accumulator)

            // Events second — resolves from cache, sees updated state, no double-archive
            if (entry.events().isNotEmpty()) {
                service.processBlockEvents(entry.events(), accumulator)
            }
        } else if (entry.events().isNotEmpty()) {
            // FAST_SYNCING: events only, possibly spanning multiple blocks
            groupByBlock(entry.events()).forEach { (blockDetails, blockEvents) ->
                accumulator.startBlock()
                service.processBlockEvents(blockEvents, accumulator)
            }
        }

        val (updated, archives) = accumulator.results()
        if (updated.isNotEmpty() || archives.isNotEmpty()) {
            service.save(updated, archives)
        }
    }
}
