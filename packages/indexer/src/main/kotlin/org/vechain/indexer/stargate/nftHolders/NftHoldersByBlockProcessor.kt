package org.vechain.indexer.stargate.nftHolders

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.checkpoint.CheckpointService

@Profile("stargate", "nft-holders-by-block")
@Component
open class NftHoldersByBlockProcessor(
    private val service: NftHoldersByBlockService,
    repository: NftHoldersByBlockRepository,
    checkpointService: CheckpointService,
) :
    BaseProcessor(
        repository = repository,
        indexerName = IndexerNames.NFT_HOLDERS_BY_BLOCK.NAME,
        checkpointService = checkpointService,
        collectionName = IndexerNames.NFT_HOLDERS_BY_BLOCK.COLLECTION,
    ) {
    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val newRecords = service.processEvents(entry.events())

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
