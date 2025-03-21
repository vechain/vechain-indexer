package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedTransferEvent

@Profile("transfer-events")
@Configuration
open class TransferIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedTransferEvent::class.java

        // Ensure Transfer Events indexes
        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index().named("transfer_blockNumber_-1").on("blockNumber", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("transfer_to_1_blockNumber_-1_txId_-1__id_-1")
                    .on("to", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("transfer_from_1_blockNumber_-1_txId_-1__id_-1")
                    .on("from", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1")
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(
                        "transfer_tokenAddress_1_eventType_1_to_1_1_blockNumber_-1_txId_-1__id_-1"
                    )
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("to", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named(
                        "transfer_tokenAddress_1_eventType_1_from_1_1_blockNumber_-1_txId_-1__id_-1"
                    )
                    .on("tokenAddress", Sort.Direction.ASC)
                    .on("eventType", Sort.Direction.ASC)
                    .on("from", Sort.Direction.ASC)
                    .on("blockNumber", Sort.Direction.DESC)
                    .on("txId", Sort.Direction.DESC)
                    .on("_id", Sort.Direction.DESC)
            )
    }
}
