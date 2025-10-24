package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.version.IndexerVersionService

@Profile("transfers")
@Component
open class FungibleTokenInteractionsProcessor(
    private val service: FungibleTokenInteractionsService,
    repository: FungibleTokenInteractionsRepository,
    indexerVersionService: IndexerVersionService,
) :
    BaseProcessor(
        repository = repository,
        indexerVersionService = indexerVersionService,
        indexerName = IndexerNames.FUNGIBLE_TOKEN_INTERACTIONS,
    ) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val transferEvents = service.processEvents(entry.events())

        if (transferEvents.isNotEmpty()) {
            service.save(transferEvents)
        }
    }
}
