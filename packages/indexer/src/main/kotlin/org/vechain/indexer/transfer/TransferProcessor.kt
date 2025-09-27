package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.utils.BlockUtils

@Profile("transfers")
@Component
open class TransferProcessor(
    private val mongoTemplate: MongoTemplate,
    repository: TransferEventRepository,
) : BaseProcessor(repository) {

    override fun process(entry: IndexingResult) {
        if (entry.events().isEmpty()) return

        val transferEvents = BlockUtils.getAllTransferEvents(entry.events())

        if (transferEvents.isNotEmpty()) {
            mongoTemplate.insert(transferEvents, IndexedTransferEvent::class.java)
        }
    }
}
