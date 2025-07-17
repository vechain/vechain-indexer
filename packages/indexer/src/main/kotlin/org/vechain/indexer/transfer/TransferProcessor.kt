package org.vechain.indexer.transfer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.BaseProcessor
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepository
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.utils.BlockUtils

@Profile("transfers")
@Component
open class TransferProcessor(
    private val mongoTemplate: MongoTemplate,
    repository: TransferEventRepository,
) : BaseProcessor(repository) {

    override fun process(events: List<IndexedEvent>, block: Block?) {
        if (events.isEmpty()) return

        val transferEvents = BlockUtils.getAllTransferEvents(events)

        if (transferEvents.isNotEmpty()) {
            mongoTemplate.insert(transferEvents, IndexedTransferEvent::class.java)
        }
    }
}
