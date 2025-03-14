package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedHistoryEvent

@Profile("history-events")
@ChangeUnit(id = "history-initializer", order = "9", author = "roisin-dowling")
class HistoryInitializerChangeUnit {
    companion object {
        val HISTORY_EVENTS = IndexedHistoryEvent::class.java
        const val BLOCK_NUMBER_IDX = "blockNumber_1"
        const val TO_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX =
            "to_1_contractAddress_1_blockTimestamp_-1"
        const val FROM_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX =
            "from_1_contractAddress_1_blockTimestamp_-1"
        const val ORIGIN_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX =
            "origin_1_contractAddress_1_blockTimestamp_-1"
        const val FROM_BLOCK_TIMESTAMP_EVENT_NAME_IDX = "from_1_blockTimestamp_-1_eventName_1"
        const val TO_BLOCK_TIMESTAMP_EVENT_NAME_IDX = "to_1_blockTimestamp_-1_eventName_1"
        const val ORIGIN_BLOCK_TIMESTAMP_EVENT_NAME_IDX = "origin_1_blockTimestamp_-1_eventName_1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(HISTORY_EVENTS)) {
            mongoTemplate.createCollection(HISTORY_EVENTS)
        }
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(BLOCK_NUMBER_IDX)
                .on(IndexedHistoryEvent::blockNumber.name, Sort.Direction.ASC)

        val toContractAddressBlockTimestampIdx: IndexDefinition =
            Index()
                .named(TO_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)

        val fromContractAddressBlockTimestampIdx: IndexDefinition =
            Index()
                .named(FROM_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)

        val originContractAddressBlockTimestampIdx: IndexDefinition =
            Index()
                .named(ORIGIN_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)

        val fromBlockTimestampEventNameIdx: IndexDefinition =
            Index()
                .named(FROM_BLOCK_TIMESTAMP_EVENT_NAME_IDX)
                .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)

        val toBlockTimestampEventNameIdx: IndexDefinition =
            Index()
                .named(TO_BLOCK_TIMESTAMP_EVENT_NAME_IDX)
                .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)

        val originBlockTimestampEventNameIdx: IndexDefinition =
            Index()
                .named(ORIGIN_BLOCK_TIMESTAMP_EVENT_NAME_IDX)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .on(IndexedHistoryEvent::eventName.name, Sort.Direction.ASC)

        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(toContractAddressBlockTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(fromContractAddressBlockTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(originContractAddressBlockTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(fromBlockTimestampEventNameIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(toBlockTimestampEventNameIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(originBlockTimestampEventNameIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(BLOCK_NUMBER_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(TO_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(FROM_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX)
        mongoTemplate
            .indexOps(HISTORY_EVENTS)
            .dropIndex(ORIGIN_CONTRACT_ADDRESS_BLOCK_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(FROM_BLOCK_TIMESTAMP_EVENT_NAME_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(TO_BLOCK_TIMESTAMP_EVENT_NAME_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(ORIGIN_BLOCK_TIMESTAMP_EVENT_NAME_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(HISTORY_EVENTS)) {
            mongoTemplate.dropCollection(HISTORY_EVENTS)
        }
    }
}
