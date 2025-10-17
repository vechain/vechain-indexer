package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("nfts", "history")
@Component
open class NftBlacklistProcessor(
    private val nftBlacklistService: NftBlacklistService,
    nftBlacklistArchiveService: ArchiveService<NftBlacklist, NftBlacklistArchive>,
    repository: NftBlacklistRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = nftBlacklistArchiveService,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.NFT_BLACKLIST,
    ) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Find any existing records
        val existing = nftBlacklistService.getExisting(entry.events())

        // Process the updated records
        val updated = nftBlacklistService.parseRecords(entry.events(), existing)

        // Finally save the updated records and archive the existing ones
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            nftBlacklistService.save(updated, existing)
        }
    }
}
