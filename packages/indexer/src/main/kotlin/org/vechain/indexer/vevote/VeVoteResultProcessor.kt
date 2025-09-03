package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    repository: VeVoteProposalResultRepository,
    archive: ArchiveService<VeVoteProposalResults, VeVoteProposalResultsArchive>,
) : BaseStatefulProcessor(repository = repository, archiveService = archive) {
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) return

        // Process votes in the service
        val updates = service.processVeVoteResults(matchedEvents)

        // Save the results
        service.save(updates.first, updates.second)
    }
}
