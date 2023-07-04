package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedTransaction

@ChangeUnit(id = "transactions-initializer", order = "3", author = "nawfal-labrahmi")
class TransactionsInitializerChangeUnit {

    companion object {
        val TRANSACTIONS = IndexedTransaction::class.java
        const val TX_BLOCKNUMBER_IDX = "tx_blockNumber_-1"
        const val TX_ORIGIN_GAS_PAYER_IDX = "tx_origin_1_gasPayer_1_blockNumber_-1__id_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.createCollection(TRANSACTIONS)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(TX_BLOCKNUMBER_IDX)
                .on(IndexedTransaction::blockNumber.name, Sort.Direction.DESC)
                .background()

        val originGasPayerIdx: IndexDefinition =
            Index()
                .named(TX_ORIGIN_GAS_PAYER_IDX)
                .on(IndexedTransaction::origin.name, Sort.Direction.ASC)
                .on(IndexedTransaction::gasPayer.name, Sort.Direction.ASC)
                .on(IndexedTransaction::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedTransaction::id.name, Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(TRANSACTIONS).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(TRANSACTIONS).ensureIndex(originGasPayerIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(TRANSACTIONS).dropIndex(TX_BLOCKNUMBER_IDX)
        mongoTemplate.indexOps(TRANSACTIONS).dropIndex(TX_ORIGIN_GAS_PAYER_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.dropCollection(TRANSACTIONS)
    }
}
