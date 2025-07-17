package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedNft
import org.vechain.indexer.model.NftArchive
import org.vechain.indexer.repository.NFTRepository
import org.vechain.indexer.thor.model.Block

@Profile("nfts")
@Component
open class NftProcessor(
    private val nftService: NftService,
    private val nftArchiveService: ArchiveService<IndexedNft, NftArchive>,
    repository: NFTRepository,
) : BaseProcessor(repository) {

    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (events.isEmpty()) return

        // Find any existing records
        val existing = nftService.getExisting(events)

        // Process the updated records
        val updated = nftService.parseRecords(events, existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftService.update(updated, existing)
        }
    }

    override fun rollback(blockNumber: Long) {
        nftArchiveService.rollback(blockNumber)
    }
}
