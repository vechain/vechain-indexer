package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService

@Profile("transaction-indexer", "prod")
@Component
open class TransactionIndexer(private val thorService: ThorService, private val txRepo: TransactionRepo) : Indexer() {
    override fun processBlock(blockNumber: Long) {
        val block = thorService.getBlock(blockNumber)
        if (block.transactions.isNotEmpty())
            txRepo.saveAll(block.transactions)
    }

    override fun getStartingBlock(): Long {
        return txRepo.getMaxBlockNumber().firstOrNull()?.blockNumber ?: 0
    }

}