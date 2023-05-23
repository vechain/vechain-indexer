package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.Block
import org.vechain.indexer.model.Transaction
import org.vechain.indexer.repos.TransactionRepo
import org.vechain.indexer.service.ThorService

@Profile("transaction-indexer")
@Component
open class TransactionIndexer(
    thorService: ThorService,
    txRepo: TransactionRepo,
    mongoTemplate: MongoTemplate
) :
    Indexer(thorService, txRepo, mongoTemplate) {
    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty()) {
            insertAll(block.transactions.map { Transaction(it) }, Transaction::class.java)
        }
    }

}