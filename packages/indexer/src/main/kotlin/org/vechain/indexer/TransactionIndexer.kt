package org.vechain.indexer

import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.repository.TransactionRepo
import org.vechain.indexer.service.ThorService
import org.vechain.thor.model.Block

@Profile("transactions")
@Component
open class TransactionIndexer(
    thorService: ThorService,
    txRepo: TransactionRepo,
    private val mongoTemplate: MongoTemplate,
) :
    VeWorldIndexer(thorService, txRepo) {

    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty()) {
            mongoTemplate.insert(
                block.transactions.map { IndexedTransaction(block, it) },
                IndexedTransaction::class.java
            )
        }
    }

}
