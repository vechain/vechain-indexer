package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedBlock

@Profile("blocks")
@ChangeUnit(id = "blocks-initializer", order = "2", author = "nawfal-labrahmi")
class BlocksInitializerChangeUnit {

    companion object {
        val BLOCKS = IndexedBlock::class.java
        const val BLOCK_BLOCKNUMBER_IDX = "block_blockNumber_-1"
        const val BLOCK_IS_FINALIZED_IDX = "block_isFinalized_1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(BLOCKS)) mongoTemplate.createCollection(BLOCKS)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(BLOCK_BLOCKNUMBER_IDX)
                .on(IndexedBlock::blockNumber.name, Sort.Direction.DESC)
                .unique()
                .background()

        val isFinalizedIdx: IndexDefinition =
            Index()
                .named(BLOCK_IS_FINALIZED_IDX)
                .on(IndexedBlock::isFinalized.name, Sort.Direction.ASC)
                .background()

        mongoTemplate.indexOps(BLOCKS).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(BLOCKS).ensureIndex(isFinalizedIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(BLOCKS).dropIndex(BLOCK_BLOCKNUMBER_IDX)
        mongoTemplate.indexOps(BLOCKS).dropIndex(BLOCK_IS_FINALIZED_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(BLOCKS)) mongoTemplate.dropCollection(BLOCKS)
    }
}
