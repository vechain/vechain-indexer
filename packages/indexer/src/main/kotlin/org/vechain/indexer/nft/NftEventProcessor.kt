package org.vechain.indexer.nft

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.version.IndexerVersionService

@Profile("nfts")
@Component
open class NftProcessor(
    private val nftService: NftService,
    private val nftArchiveService: ArchiveService<IndexedNft, NftArchive>,
    repository: NftRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.NFT,
    ) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        // Filter out blacklist and whitelist events and handle them separately
        val (blacklistEvents, historyEvents) =
            entry
                .events()
                .partition({
                    it.eventType == "NFT_Blacklisted" || it.eventType == "NFT_Whitelisted"
                })

        if (historyEvents.isNotEmpty()) {

            // Find any existing records
            val existing = nftService.getExisting(entry.events())

            // Process the updated records
            val updated = nftService.parseRecords(entry.events(), existing)

            // Finally save the updated records and archive the existing ones
            if (updated.isNotEmpty() || existing.isNotEmpty()) {
                nftService.save(updated, existing)
            }
        }

        if (blacklistEvents.isNotEmpty()) {
            nftService.processBlacklistEvents(blacklistEvents)
        }
    }

    override fun rollback(blockNumber: Long) {
        nftArchiveService.rollback(blockNumber)
    }
}
