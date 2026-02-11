package org.vechain.indexer.b3tr.gm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseStatefulProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.gm.repository.GmNftRepository
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("b3tr", "b3tr-gm-nft")
@Component
open class GmNftProcessor(
    repository: GmNftRepository,
    gmNftArchiveService: ArchiveService<GmNft, GmNftArchive>,
    private val service: GmNftService,
    checkpointService: CheckpointService,
) :
    BaseStatefulProcessor(
        repository = repository,
        archiveService = gmNftArchiveService,
        indexerName = IndexerNames.GM_NFT.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.GM_NFT.COLLECTION,
    ) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        // Process the events using the service
        val (updated, existing) = service.processEvents(entry.events())

        // Save the updated NFTs and archives
        if (updated.isNotEmpty() || existing.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(updated, existing) }
        }
    }
}
