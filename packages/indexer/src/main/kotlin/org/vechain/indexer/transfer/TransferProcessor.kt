package org.vechain.indexer.transfer

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexerNames
import org.vechain.indexer.IndexingResult

@Profile("transfers")
@Component
open class TransferProcessor(
    private val service: TransferService,
    repository: TransferEventRepository,
) : BaseProcessor(repository = repository, indexerName = IndexerNames.TRANSFER) {

    override suspend fun processEntry(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val transferEvents = service.processEvents(entry.events())

        if (transferEvents.isNotEmpty()) {
            withContext(Dispatchers.IO) { service.save(transferEvents) }
        }
    }
}
