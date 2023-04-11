package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService
import org.vechain.indexer.utils.BlockUtils

@Profile("transaction-indexer", "prod")
@Component
open class TransactionIndexer(private val thorService: ThorService, private val txRepo: TransactionRepo) :
    Indexer(thorService) {
    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty()) {
            val txs: List<WrappedTransaction> = BlockUtils.getAllTransactions(block)
            txRepo.saveAll(txs)
        }
    }

    override fun getStartingBlock(): Long {
        return txRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}