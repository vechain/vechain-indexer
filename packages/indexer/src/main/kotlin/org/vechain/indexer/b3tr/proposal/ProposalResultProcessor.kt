package org.vechain.indexer.b3tr.proposal

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
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
        indexerName = "ProposalResultIndexer",
    ) {
    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
