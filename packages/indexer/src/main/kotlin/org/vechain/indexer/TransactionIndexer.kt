package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.event.BusinessEventManager
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.repository.TransactionRepository
import org.vechain.indexer.service.TransactionService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

@Profile("transactions")
@Component
open class TransactionIndexer(
    private val txRepo: TransactionRepository,
    private val transactionService: TransactionService,
    private val mongoTemplate: MongoTemplate,
    thorClient: ThorClient,
    abiManager: AbiManager,
    businessEventManager: BusinessEventManager,
    @Value("\${indexer.startBlock.transactions}") private val startBlock: Long,
    @Value("\${indexer.syncLogInterval.transactions}") private val syncLogInterval: Long,
) :
    BaseIndexer(
        repository = txRepo,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        abiManager = abiManager,
        businessEventManager = businessEventManager,
    ) {
    override fun rollback(blockNumber: Long) {
        txRepo.deleteAllByBlockNumberBetween(blockNumber - 1, blockNumber + 1)
    }

    override fun processBlock(block: Block) {
        val eventsByTx = processBlockGenericEvents(block).groupBy { it.first.txId }

        val transactions =
            transactionService.processBlockTransactions(
                block.transactions,
                eventsByTx,
                block,
            )

        mongoTemplate.insert(transactions, IndexedTransaction::class.java)
    }
}
