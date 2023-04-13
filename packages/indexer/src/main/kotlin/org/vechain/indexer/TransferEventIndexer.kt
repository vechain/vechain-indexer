package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.TransferEventRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils

@Profile("transfer-event-indexer", "prod")
@Component
open class TransferEventIndexer(
    private val thorService: ThorService,
    private val transferEventRepo: TransferEventRepo
) : Indexer(thorService, transferEventRepo) {

    override fun processBlock(block: Block) {

        val transferEvents = BlockUtils.getTransferEvents(block)

        if (transferEvents.isNotEmpty()) transferEventRepo.saveAll(transferEvents)
    }

}