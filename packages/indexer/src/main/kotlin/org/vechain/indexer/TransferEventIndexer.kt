package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.TransferEvent
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils

@Profile("transfer-events")
@Component
open class TransferEventIndexer(
    thorService: ThorService,
    transferEventRepo: TransferEventRepo,
    mongoTemplate: MongoTemplate
) : Indexer(thorService, transferEventRepo, mongoTemplate) {

    override fun processBlock(block: Block) {

        val transferEvents = BlockUtils.getAllTransferEvents(block)

        if (transferEvents.isNotEmpty()) insertAll(transferEvents, TransferEvent::class.java)
    }

}
