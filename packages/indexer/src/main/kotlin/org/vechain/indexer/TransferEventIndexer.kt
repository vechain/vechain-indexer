package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block

@Profile("transfer-events")
@Component
open class TransferEventIndexer(
    thorService: ThorService,
    transferEventRepo: TransferEventRepo,
    private val mongoTemplate: MongoTemplate,

    ) : VeWorldIndexer(thorService, transferEventRepo) {

    override fun processBlock(block: Block) {

        val transferEvents = BlockUtils.getAllTransferEvents(block)

        if (transferEvents.isNotEmpty()) mongoTemplate.insert(transferEvents, IndexedTransferEvent::class.java)
    }

}
