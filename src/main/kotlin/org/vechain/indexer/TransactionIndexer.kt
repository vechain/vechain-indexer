package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.WrappedTransaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService

@Profile("transaction-indexer", "prod")
@Component
class TransactionIndexer(private val thorService: ThorService, private val txRepo: TransactionRepo) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        if (block.transactions.isNotEmpty())
            txRepo.saveAll(block.transactions.map { WrappedTransaction(block, it) })
    }

    override fun getStartingBlock(): Long {
        return txRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}