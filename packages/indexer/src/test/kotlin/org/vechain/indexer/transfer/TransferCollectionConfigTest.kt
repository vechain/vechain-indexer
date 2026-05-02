package org.vechain.indexer.transfer

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexOperations
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class TransferCollectionConfigTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates latest transfers canonical index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(IndexedTransferEvent::class.java) } returns true
        every { mongoTemplate.getCollectionName(IndexedTransferEvent::class.java) } returns
            "transfer_events"
        every { mongoTemplate.indexOps(IndexedTransferEvent::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        TransferCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexOptions["name"] == "eventType_1_blockNumber_-1_transferIndex_1" &&
                    it.indexKeys["eventType"] == 1 &&
                    it.indexKeys["blockNumber"] == -1 &&
                    it.indexKeys["transferIndex"] == 1
            }
        )
    }
}
