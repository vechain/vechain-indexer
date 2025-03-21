package org.vechain.indexer.config.mongo

import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.vechain.indexer.model.IndexedHistoryEvent

@Profile("history-events")
@Configuration
open class HistoryIndexConfig(private val mongoTemplate: MongoTemplate) : IndexConfig {

    @PostConstruct
    override fun initIndexes() {
        val modelObj = IndexedHistoryEvent::class.java

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(Index().named("blockNumber_1").on("blockNumber", Sort.Direction.ASC))

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("to_1_contractAddress_1_blockTimestamp_-1")
                    .on("to", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("from_1_contractAddress_1_blockTimestamp_-1")
                    .on("from", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("origin_1_contractAddress_1_blockTimestamp_-1")
                    .on("origin", Sort.Direction.ASC)
                    .on("contractAddress", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("from_1_blockTimestamp_-1_eventName_1")
                    .on("from", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("to_1_blockTimestamp_-1_eventName_1")
                    .on("to", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("origin_1_blockTimestamp_-1_eventName_1")
                    .on("origin", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )

        mongoTemplate
            .indexOps(modelObj)
            .ensureIndex(
                Index()
                    .named("gasPayer_1_blockTimestamp_-1_eventName_1")
                    .on("gasPayer", Sort.Direction.ASC)
                    .on("blockTimestamp", Sort.Direction.DESC)
                    .on("eventName", Sort.Direction.ASC)
            )
    }
}
