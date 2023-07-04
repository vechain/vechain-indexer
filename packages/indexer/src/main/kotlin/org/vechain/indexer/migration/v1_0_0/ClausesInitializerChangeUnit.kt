package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedClause

@ChangeUnit(id = "clauses-initializer", order = "4", author = "nawfal-labrahmi")
class ClausesInitializerChangeUnit {

    companion object {
        val CLAUSES = IndexedClause::class.java
        const val CLAUSE_BLOCKNUMBER_IDX = "clause_blockNumber_-1"
        const val CLAUSE_ORIGIN_IDX = "clause_origin_1_blockNumber_-1_txId_-1__id_-1"
        const val CLAUSE_TO_IDX = "clause_to_1_blockNumber_-1_txId_-1__id_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(CLAUSES)) mongoTemplate.createCollection(CLAUSES)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(CLAUSE_BLOCKNUMBER_IDX)
                .on(IndexedClause::blockNumber.name, Sort.Direction.DESC)
                .background()

        val originIdx: IndexDefinition =
            Index()
                .named(CLAUSE_ORIGIN_IDX)
                .on(IndexedClause::origin.name, Sort.Direction.ASC)
                .on(IndexedClause::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedClause::txId.name, Sort.Direction.DESC)
                .on(IndexedClause::id.name, Sort.Direction.DESC)
                .background()

        val toIdx: IndexDefinition =
            Index()
                .named(CLAUSE_TO_IDX)
                .on(IndexedClause::to.name, Sort.Direction.ASC)
                .on(IndexedClause::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedClause::txId.name, Sort.Direction.DESC)
                .on(IndexedClause::id.name, Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(CLAUSES).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(CLAUSES).ensureIndex(originIdx)
        mongoTemplate.indexOps(CLAUSES).ensureIndex(toIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(CLAUSES).dropIndex(CLAUSE_BLOCKNUMBER_IDX)
        mongoTemplate.indexOps(CLAUSES).dropIndex(CLAUSE_ORIGIN_IDX)
        mongoTemplate.indexOps(CLAUSES).dropIndex(CLAUSE_TO_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(CLAUSES)) mongoTemplate.dropCollection(CLAUSES)
    }
}
