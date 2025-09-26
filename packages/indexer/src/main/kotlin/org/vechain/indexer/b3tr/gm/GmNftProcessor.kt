package org.vechain.indexer.b3tr.gm

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.timing.WithTiming

@Profile("b3tr", "b3tr-gm-nft")
@Component
open class GmNftProcessor(
    repository: GmNftRepository,
    gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
    private val service: GmNftService,
) : BaseStatefulProcessor(repository = repository, archiveService = gmNftArchiveService) {

    @WithTiming("GmNftProcessor.process")
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
