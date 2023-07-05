package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.Archive

@ChangeUnit(id = "archives-initializer", order = "1", author = "nawfal-labrahmi")
class ArchivesInitializerChangeUnit {

    companion object {
        val ARCHIVES = Archive::class.java
        const val ARCHIVE_BLOCKNUMBER_IDX = "archive_blockNumber_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(ARCHIVES)) mongoTemplate.createCollection(ARCHIVES)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val idx: IndexDefinition =
            Index()
                .named(ARCHIVE_BLOCKNUMBER_IDX)
                .on("data.blockNumber", Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(ARCHIVES).ensureIndex(idx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(ARCHIVES).dropIndex(ARCHIVE_BLOCKNUMBER_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(ARCHIVES)) mongoTemplate.dropCollection(ARCHIVES)
    }
}
