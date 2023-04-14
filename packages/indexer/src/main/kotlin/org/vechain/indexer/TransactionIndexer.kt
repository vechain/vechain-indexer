package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService

@Profile("transaction-indexer", "prod")
@Component
open class TransactionIndexer(private val thorService: ThorService, private val txRepo: TransactionRepo) :
    Indexer(thorService, txRepo) {
    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty())
            txRepo.saveAll(block.transactions)
    }

}