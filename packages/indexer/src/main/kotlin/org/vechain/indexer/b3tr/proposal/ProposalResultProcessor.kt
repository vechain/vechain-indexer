package org.vechain.indexer.b3tr.proposal

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Profile("b3tr", "b3tr-proposal", "b3tr-proposal-results")
@Component
open class ProposalResultProcessor(
    repository: ProposalResultRepository,
    proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>,
    private val service: ProposalResultService,
) : BaseStatefulProcessor(repository = repository, archiveService = proposalResultArchiveService) {
    @WithTiming("ProposalResultProcessor.process")
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, archives) = service.processEvents(matchedEvents)

        // Save the updated NFTs and archives
        service.save(updated, archives)
    }
}
