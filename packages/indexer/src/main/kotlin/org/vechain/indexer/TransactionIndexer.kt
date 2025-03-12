package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.event.AbiManager
import org.vechain.indexer.repository.TransactionRepository
import org.vechain.indexer.service.TransactionService
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block

@Profile("transactions")
@Component
open class TransactionIndexer(
    txRepo: TransactionRepository,
    private val transactionService: TransactionService,
    thorClient: ThorClient,
    abiManager: AbiManager,
    @Value("\${indexer.startBlock.transactions}") startBlock: Long,
    @Value("\${indexer.syncLogInterval.transactions}") private val syncLogInterval: Long,
) :
    BaseIndexer(
        repository = txRepo,
        startBlock = startBlock,
        thorClient = thorClient,
        syncLogInterval = syncLogInterval,
        abiManager = abiManager,
    ) {
    override fun rollback(blockNumber: Long) {
        transactionService.rollback(blockNumber)
    }

    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty()) {
            val eventsByTx = processBlockGenericEvents(block).groupBy { it.txId }

            transactionService.processBlockTransactions(
                block.transactions,
                eventsByTx,
                block,
            )
        }
    }
}
