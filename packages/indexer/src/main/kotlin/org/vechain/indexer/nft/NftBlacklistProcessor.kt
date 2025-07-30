package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@Profile("nfts")
@Component
open class NftBlacklistProcessor(
    private val nftBlacklistService: NftBlacklistService,
    nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>,
    repository: NftBlacklistRepository,
) : BaseStatefulProcessor(repository = repository, archiveService = nftBlacklistArchiveService) {

    override fun process(matchedEvents: List<IndexedEvent>, block: Block?) {
        if (matchedEvents.isEmpty()) return

        // Find any existing records
        val existing = nftBlacklistService.getExisting(matchedEvents)

        // Process the updated records
        val updated = nftBlacklistService.parseRecords(matchedEvents, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftBlacklistService.update(updated, existing)
        }
    }
}
