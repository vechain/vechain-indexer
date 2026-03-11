package org.vechain.indexer.history

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexOperations
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class HistoryCollectionConfigTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates query-aligned history indexes`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(IndexedHistoryEvent::class.java) } returns true
        every { mongoTemplate.indexOps(IndexedHistoryEvent::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        HistoryCollectionConfig(
                mongoTemplate = mongoTemplate,
                indexerVersionService = indexerVersionService,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["origin"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "origin_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["tokenId"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "tokenId_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["to"] == 1 &&
                    it.indexKeys["eventName"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "to_1_eventName_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["to"] == 1 &&
                    it.indexKeys["appId"] == 1 &&
                    it.indexKeys["eventName"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "to_1_appId_1_eventName_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["appId"] == 1 &&
                    it.indexKeys["eventName"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "appId_1_eventName_1_blockTimestamp_-1"
            }
        )
        assertFalse(capturedIndexes.any { it.indexOptions["name"] == "isBlacklisted_1" })
    }

    @Test
    fun `initCollection fails when index creation fails`() {
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(IndexedHistoryEvent::class.java) } returns true
        every { mongoTemplate.indexOps(IndexedHistoryEvent::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(any()) } throws RuntimeException("boom")

        val error =
            assertThrows(IllegalStateException::class.java) {
                HistoryCollectionConfig(
                        mongoTemplate = mongoTemplate,
                        indexerVersionService = indexerVersionService,
                        appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                    )
                    .initCollection()
            }

        assertEquals("Failed to create index blockNumber_-1 for IndexedHistoryEvent", error.message)
    }
}
