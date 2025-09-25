package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timing.WithTiming

@Profile("vevote", "vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    private val repository: VeVoteProposalResultRepository,
    veVoteResultArchiveService: ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>,
) : BaseStatefulProcessor(repository = repository, archiveService = veVoteResultArchiveService) {

    @WithTiming("VeVoteResultProcessor.process")
    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) return

        // Process votes in the service
        val (updated, archives) = service.processEvents(matchedEvents)

        // Save the results
        service.save(updated, archives)
    }
}
