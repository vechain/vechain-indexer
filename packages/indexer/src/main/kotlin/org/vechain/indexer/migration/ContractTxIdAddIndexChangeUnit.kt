package org.vechain.indexer.migration

import io.mongock.api.annotations.ChangeUnit
import io.mongock.api.annotations.Execution
import io.mongock.api.annotations.RollbackExecution
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedContract


@ChangeUnit(id = "contract-migration", order = "1", author = "nlab")
class ContractTxIdAddIndexChangeUnit {

    companion object {
        const val CONTRACTS_TX_ID_IDX = "contracts_txId_1"
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val idx: IndexDefinition =
            Index()
                .named(CONTRACTS_TX_ID_IDX)
                .on(IndexedContract::txId.name, Sort.Direction.DESC)

        mongoTemplate.indexOps(IndexedContract::class.java).ensureIndex(idx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(IndexedContract::class.java).dropIndex(CONTRACTS_TX_ID_IDX)
    }
}