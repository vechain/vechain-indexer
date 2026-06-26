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
        every { mongoTemplate.getCollectionName(IndexedHistoryEvent::class.java) } returns
            "history_events"
        every { mongoTemplate.indexOps(IndexedHistoryEvent::class.java) } returns indexOperations
        every { indexOperations.indexInfo } returns emptyList()
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        HistoryCollectionConfig(
                mongoTemplate = mongoTemplate,
                indexerVersionService = indexerVersionService,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )
            .apply {
                initCollection()
                createPendingIndexes()
            }

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["involvedAddresses"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexKeys["eventName"] == 1 &&
                    it.indexOptions["name"] == "involvedAddresses_1_blockTimestamp_-1_eventName_1"
            }
        )
        // Per-address indexes for GET /history?searchBy=... — origin/from/gasPayer are
        // needed because that path builds a $or over caller-specified fields and can't use
        // involvedAddresses. `to` is covered by to_1_eventName_1_blockTimestamp_-1; `owner`
        // isn't in ValidSearchBy.
        listOf(
                "origin_1_blockTimestamp_-1",
                "from_1_blockTimestamp_-1",
                "gasPayer_1_blockTimestamp_-1",
            )
            .forEach { name ->
                assertTrue(
                    capturedIndexes.any { it.indexOptions["name"] == name },
                    "$name should be registered to serve searchBy=$name queries",
                )
            }
        listOf("to_1_blockTimestamp_-1", "owner_1_blockTimestamp_-1").forEach { name ->
            assertFalse(
                capturedIndexes.any { it.indexOptions["name"] == name },
                "$name should not be registered — covered by wider indexes or not in ValidSearchBy",
            )
        }
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["tokenId"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "tokenId_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["tokenId"] == 1 &&
                    it.indexKeys["eventName"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] == "tokenId_1_eventName_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["contractAddress"] == 1 &&
                    it.indexKeys["tokenId"] == 1 &&
                    it.indexKeys["eventName"] == 1 &&
                    it.indexKeys["blockTimestamp"] == -1 &&
                    it.indexOptions["name"] ==
                        "contractAddress_1_tokenId_1_eventName_1_blockTimestamp_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["delegationId"] == 1 &&
                    it.indexKeys["blockNumber"] == -1 &&
                    it.indexKeys["delegationLifecycleOrder"] == -1 &&
                    it.indexOptions["name"] ==
                        "delegationId_1_blockNumber_-1_delegationLifecycleOrder_-1"
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
        every { mongoTemplate.getCollectionName(IndexedHistoryEvent::class.java) } returns
            "history_events"
        every { mongoTemplate.indexOps(IndexedHistoryEvent::class.java) } returns indexOperations
        every { indexOperations.indexInfo } returns emptyList()
        every { indexOperations.createIndex(any()) } throws RuntimeException("boom")

        val error =
            assertThrows(IllegalStateException::class.java) {
                HistoryCollectionConfig(
                        mongoTemplate = mongoTemplate,
                        indexerVersionService = indexerVersionService,
                        appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                    )
                    .apply {
                        initCollection()
                        createPendingIndexes()
                    }
            }

        assertEquals("Failed to create index blockNumber_-1 for IndexedHistoryEvent", error.message)
    }
}
