package org.vechain.indexer

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.stereotype.Component
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.repository.TransactionRepository
import org.vechain.thor.model.Block

@Profile("transactions")
@Component
open class TransactionIndexer(
    txRepo: TransactionRepository,
    private val mongoTemplate: MongoTemplate,
    @Value("\${thor.url}") private val thorUrl: String,
    @Value("\${indexer.startBlock.transactions}") private val startBlock: Long,
) :
    VeWorldIndexer(txRepo, thorUrl, startBlock) {

    override fun processBlock(block: Block) {
        if (block.transactions.isNotEmpty()) {
            mongoTemplate.insert(
                block.transactions.map { IndexedTransaction(block, it) },
                IndexedTransaction::class.java
            )
        }
    }

}
