package org.vechain.indexer.stargate

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("stargate", "nft-holders-by-block")
@Component
open class NftHoldersByBlockProcessor(
    private val service: NftHoldersByBlockService,
    repository: NftHoldersByBlockRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = "NftHoldersByBlockIndexer",
    ) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) {
            return
        }

        val newRecords = service.processEvents(entry.events())

        if (newRecords.isNotEmpty()) {
            service.saveRecords(newRecords)
        }
    }
}
