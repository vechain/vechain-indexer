package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService

@Profile("nfts")
@Component
open class NftProcessor(
    private val nftService: NftService,
    private val nftArchiveService: ArchiveService<IndexedNft, NftArchive>,
    repository: NftRepository,
) : BaseProcessor(repository) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Find any existing records
        val existing = nftService.getExisting(entry.events())

        // Process the updated records
        val updated = nftService.parseRecords(entry.events(), existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftService.save(updated, existing)
        }
    }

    override fun rollback(blockNumber: Long) {
        nftArchiveService.rollback(blockNumber)
    }
}
