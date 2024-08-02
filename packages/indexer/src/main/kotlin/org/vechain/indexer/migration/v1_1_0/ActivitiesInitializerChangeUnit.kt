package org.vechain.indexer.migration.v1_1_0

import io.mongock.api.annotations.*
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedActivity

@Profile("activities")
@ChangeUnit(id = "activities-initializer", order = "8", author = "darrenvechain")
class ActivitiesInitializerChangeUnit {

    companion object {
        val TRANSACTIONS = IndexedActivity::class.java
        const val ACTIVITY_BLOCKNUMBER_IDX = "tx_blockNumber_-1"
        const val ACTIVITY_ACCOUNT_IDX = "tx_account_1_blockNumber_-1__id_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(TRANSACTIONS))
            mongoTemplate.createCollection(TRANSACTIONS)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(ACTIVITY_BLOCKNUMBER_IDX)
                .on(IndexedActivity::blockNumber.name, Sort.Direction.DESC)
                .background()

        val originIdx: IndexDefinition =
            Index()
                .named(ACTIVITY_ACCOUNT_IDX)
                .on(IndexedActivity::account.name, Sort.Direction.ASC)
                .on(IndexedActivity::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedActivity::id.name, Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(TRANSACTIONS).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(TRANSACTIONS).ensureIndex(originIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(TRANSACTIONS).dropIndex(ACTIVITY_BLOCKNUMBER_IDX)
        mongoTemplate.indexOps(TRANSACTIONS).dropIndex(ACTIVITY_ACCOUNT_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(TRANSACTIONS)) mongoTemplate.dropCollection(TRANSACTIONS)
    }
}
