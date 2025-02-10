package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedHistoryEvent

@Profile("history_events")
@ChangeUnit(id = "history-initializer", order = "9", author = "roisin-dowling")
class HistoryInitializerChangeUnit {
    companion object {
        val HISTORY_EVENTS = IndexedHistoryEvent::class.java
        const val TO_FROM_ORIGIN_TIMESTAMP_IDX = "to_1_from_1_origin_1_blockTimestamp_-1"
        const val TO_FROM_ORIGIN_CONTRACT_ADDRESS_TIMESTAMP_IDX =
            "to_1_from_1_origin_1_contractAddress_1_blockTimestamp_-1"
        const val GAS_PAYER_TIMESTAMP_IDX = "gasPayer_1_blockTimestamp_-1"
        const val ORIGIN_GAS_PAYER_TIMESTAMP_IDX = "origin_1_gasPayer_1_blockTimestamp_-1"
        const val EVENT_TYPE_TO_FROM_ORIGIN_TIMESTAMP_IDX =
            "eventType_1_to_1_from_1_origin_1_blockTimestamp_-1"
        const val EVENT_TYPE_TO_FROM_CONTRACT_ADDRESS_TIMESTAMP_IDX =
            "eventType_1_to_1_from_1_contractAddress_1_blockTimestamp_-1"
        const val EVENT_TYPE_ORIGIN_TIMESTAMP_IDX = "eventType_1_origin_1_blockTimestamp_-1"
        const val EVENT_TYPE_GAS_PAYER_TIMESTAMP_IDX = "eventType_1_gasPayer_1_blockTimestamp_-1"
        const val BLOCK_NUMBER_IDX = "blockNumber_1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(HISTORY_EVENTS)) {
            mongoTemplate.createCollection(HISTORY_EVENTS)
        }
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val toFromOriginTimestampIdx: IndexDefinition =
            Index()
                .named(TO_FROM_ORIGIN_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val toFromOriginContractAddressTimestampIdx: IndexDefinition =
            Index()
                .named(TO_FROM_ORIGIN_CONTRACT_ADDRESS_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val gasPayerTimestampIdx: IndexDefinition =
            Index()
                .named(GAS_PAYER_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::gasPayer.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val originGasPayerTimestampIdx: IndexDefinition =
            Index()
                .named(ORIGIN_GAS_PAYER_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::gasPayer.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val eventTypeToFromOriginTimestampIdx: IndexDefinition =
            Index()
                .named(EVENT_TYPE_TO_FROM_ORIGIN_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::eventType.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val eventTypeToFromContractAddressTimestampIdx: IndexDefinition =
            Index()
                .named(EVENT_TYPE_TO_FROM_CONTRACT_ADDRESS_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::eventType.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::to.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::from.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::contractAddress.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val eventTypeOriginTimestampIdx: IndexDefinition =
            Index()
                .named(EVENT_TYPE_ORIGIN_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::eventType.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::origin.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val eventTypeGasPayerTimestampIdx: IndexDefinition =
            Index()
                .named(EVENT_TYPE_GAS_PAYER_TIMESTAMP_IDX)
                .on(IndexedHistoryEvent::eventType.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::gasPayer.name, Sort.Direction.ASC)
                .on(IndexedHistoryEvent::blockTimestamp.name, Sort.Direction.DESC)
                .background()

        val blockNumberIdx: IndexDefinition =
            Index()
                .named(BLOCK_NUMBER_IDX)
                .on(IndexedHistoryEvent::blockNumber.name, Sort.Direction.ASC)
                .background()

        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(toFromOriginTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(toFromOriginContractAddressTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(gasPayerTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(originGasPayerTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(eventTypeToFromOriginTimestampIdx)
        mongoTemplate
            .indexOps(HISTORY_EVENTS)
            .ensureIndex(eventTypeToFromContractAddressTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(eventTypeGasPayerTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(eventTypeOriginTimestampIdx)
        mongoTemplate.indexOps(HISTORY_EVENTS).ensureIndex(blockNumberIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(TO_FROM_ORIGIN_TIMESTAMP_IDX)
        mongoTemplate
            .indexOps(HISTORY_EVENTS)
            .dropIndex(TO_FROM_ORIGIN_CONTRACT_ADDRESS_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(GAS_PAYER_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(ORIGIN_GAS_PAYER_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(EVENT_TYPE_TO_FROM_ORIGIN_TIMESTAMP_IDX)
        mongoTemplate
            .indexOps(HISTORY_EVENTS)
            .dropIndex(EVENT_TYPE_TO_FROM_CONTRACT_ADDRESS_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(EVENT_TYPE_GAS_PAYER_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(EVENT_TYPE_ORIGIN_TIMESTAMP_IDX)
        mongoTemplate.indexOps(HISTORY_EVENTS).dropIndex(BLOCK_NUMBER_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(HISTORY_EVENTS))
            mongoTemplate.dropCollection(HISTORY_EVENTS)
    }
}
