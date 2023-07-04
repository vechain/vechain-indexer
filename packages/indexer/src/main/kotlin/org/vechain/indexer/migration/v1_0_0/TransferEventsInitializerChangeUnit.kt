package org.vechain.indexer.migration.v1_0_0

import io.mongock.api.annotations.*
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.Index
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.vechain.indexer.model.IndexedTransferEvent

@ChangeUnit(id = "transfer-events-initializer", order = "7", author = "nawfal-labrahmi")
class TransferEventsInitializerChangeUnit {

    companion object {
        val TRANSFER_EVENTS = IndexedTransferEvent::class.java
        const val TRANSFER_BLOCKNUMBER_IDX = "transfer_blockNumber_-1"
        const val TRANSFER_TO_IDX = "transfer_to_1_blockNumber_-1_txId_-1__id_-1"
        const val TRANSFER_FROM_IDX = "transfer_from_1_blockNumber_-1_txId_-1__id_-1"
        const val TRANSFER_TOKEN_ADDRESS_IDX =
            "transfer_tokenAddress_1_blockNumber_-1_txId_-1__id_-1"
    }

    @BeforeExecution
    fun beforeExecution(mongoTemplate: MongoTemplate) {
        if (!mongoTemplate.collectionExists(TRANSFER_EVENTS))
            mongoTemplate.createCollection(TRANSFER_EVENTS)
    }

    @Execution
    fun execution(mongoTemplate: MongoTemplate) {
        val blockNumberIdx: IndexDefinition =
            Index()
                .named(TRANSFER_BLOCKNUMBER_IDX)
                .on(IndexedTransferEvent::blockNumber.name, Sort.Direction.DESC)
                .background()

        val toIdx: IndexDefinition =
            Index()
                .named(TRANSFER_TO_IDX)
                .on(IndexedTransferEvent::to.name, Sort.Direction.ASC)
                .on(IndexedTransferEvent::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedTransferEvent::txId.name, Sort.Direction.DESC)
                .on(IndexedTransferEvent::id.name, Sort.Direction.DESC)
                .background()

        val fromIdx: IndexDefinition =
            Index()
                .named(TRANSFER_FROM_IDX)
                .on(IndexedTransferEvent::from.name, Sort.Direction.ASC)
                .on(IndexedTransferEvent::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedTransferEvent::txId.name, Sort.Direction.DESC)
                .on(IndexedTransferEvent::id.name, Sort.Direction.DESC)
                .background()

        val tokenAddressIdx: IndexDefinition =
            Index()
                .named(TRANSFER_TOKEN_ADDRESS_IDX)
                .on(IndexedTransferEvent::tokenAddress.name, Sort.Direction.ASC)
                .on(IndexedTransferEvent::blockNumber.name, Sort.Direction.DESC)
                .on(IndexedTransferEvent::txId.name, Sort.Direction.DESC)
                .on(IndexedTransferEvent::id.name, Sort.Direction.DESC)
                .background()

        mongoTemplate.indexOps(TRANSFER_EVENTS).ensureIndex(blockNumberIdx)
        mongoTemplate.indexOps(TRANSFER_EVENTS).ensureIndex(toIdx)
        mongoTemplate.indexOps(TRANSFER_EVENTS).ensureIndex(fromIdx)
        mongoTemplate.indexOps(TRANSFER_EVENTS).ensureIndex(tokenAddressIdx)
    }

    @RollbackExecution
    fun rollbackExecution(mongoTemplate: MongoTemplate) {
        mongoTemplate.indexOps(TRANSFER_EVENTS).dropIndex(TRANSFER_BLOCKNUMBER_IDX)
        mongoTemplate.indexOps(TRANSFER_EVENTS).dropIndex(TRANSFER_TO_IDX)
        mongoTemplate.indexOps(TRANSFER_EVENTS).dropIndex(TRANSFER_FROM_IDX)
        mongoTemplate.indexOps(TRANSFER_EVENTS).dropIndex(TRANSFER_TOKEN_ADDRESS_IDX)
    }

    @RollbackBeforeExecution
    fun rollbackBeforeExecution(mongoTemplate: MongoTemplate) {
        if (mongoTemplate.collectionExists(TRANSFER_EVENTS))
            mongoTemplate.dropCollection(TRANSFER_EVENTS)
    }
}
