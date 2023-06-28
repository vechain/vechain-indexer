package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedTransferEvent
import org.vechain.indexer.repository.TransferEventRepository
import org.vechain.indexer.utils.BlockUtils
import org.vechain.thor.model.Block

@Profile("transfer-events")
@Component
open class TransferEventIndexer(
  private val transferEventRepository: TransferEventRepository,
  private val mongoTemplate: MongoTemplate,
  @Value("\${thor.url}") private val thorUrl: String,
  @Value("\${indexer.startBlock.transfers}") private val startBlock: Long,
) : VeWorldIndexer(transferEventRepository, startBlock, thorUrl) {

    override fun processBlock(block: Block) {

        val transferEvents = BlockUtils.getAllTransferEvents(block)

        if (transferEvents.isNotEmpty())
          mongoTemplate.insert(transferEvents, IndexedTransferEvent::class.java)
    }

    override fun rollback(blockNumber: Long) {
        transferEventRepository.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }
}
