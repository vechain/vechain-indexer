package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedTransaction

@Profile("transactions")
@Configuration
open class TransactionIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedTransaction::class.java

        // Ensure Transaction indexes
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(Index().named("tx_blockNumber_-1").on("blockNumber", Sort.Direction.DESC))

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("tx_origin_1_blockNumber_-1__id_-1")
                    .on("origin", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("tx_gasPayer_1_blockNumber_-1__id_-1")
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("tx_origin_1_gasPayer_1_blockNumber_-1__id_-1")
                    .on("origin", Sort.Direction.ASC)
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )
    }
}
