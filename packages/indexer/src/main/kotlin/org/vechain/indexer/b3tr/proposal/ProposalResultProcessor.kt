package org.vechain.indexer.b3tr.proposal

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.version.IndexerVersionService

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Component
open class ProposalResultProcessor(
    repository: ProposalResultRepository,
    proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>,
    private val service: ProposalResultService,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = proposalResultArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.PROPOSAL_RESULT,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        val allUpdated = mutableMapOf<String, ProposalResult>()
        val allArchives = mutableMapOf<String, ProposalResult>()

        // If the block object is available we will assume we're fully synced and attempt to update
        // the states
        if (entry is IndexingResult.Normal) {
            val blockDetails =
                BlockDetails(
                    blockId = entry.block.id,
                    blockNumber = entry.block.number,
                    blockTimestamp = entry.block.timestamp,
                )
            val (u, a) = service.getUpdatedStatuses(blockDetails)
            u.forEach { allUpdated[it.proposalId] = it }
            a.forEach { allArchives[it.proposalId] = it }
        }

        if (entry.events().isNotEmpty()) {
            // Process the events using the service
            val (updated, archives) = service.processEvents(entry.events())
            updated.forEach { eventResult ->
                val existing = allUpdated[eventResult.proposalId]
                if (existing != null) {
                    // Merge: preserve state from status update, use results and other fields from
                    // events
                    allUpdated[eventResult.proposalId] = eventResult.copy(state = existing.state)
                } else {
                    allUpdated[eventResult.proposalId] = eventResult
                }
            }
            archives.forEach { allArchives[it.proposalId] = it }
        }

        // Save all updated results and their archives in one call
        if (allUpdated.isNotEmpty() || allArchives.isNotEmpty()) {
            service.save(allUpdated.values.toList(), allArchives.values.toList())
        }
    }
}
