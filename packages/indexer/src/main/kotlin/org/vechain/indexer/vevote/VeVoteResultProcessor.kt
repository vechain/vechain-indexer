package org.vechain.indexer.vevote

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService

@Profile("vevote", "vevote-results")
@Component
open class VeVoteResultProcessor(
    private val service: VeVoteResultService,
    private val repository: VeVoteProposalResultRepository,
    veVoteResultArchiveService: ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>,
) : BaseStatefulProcessor(repository = repository, archiveService = veVoteResultArchiveService) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Process votes in the service
        val (updated, archives) = service.processEvents(entry.events())

        // Save the results
        service.save(updated, archives)
    }
}
