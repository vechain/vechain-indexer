package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexOperations
import org.vechain.indexer.blocks.mongo.BlocksCollectionConfig
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class BlocksCollectionConfigTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates exactly one blockNumber descending index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(IndexedBlock::class.java) } returns true
        every { mongoTemplate.getCollectionName(IndexedBlock::class.java) } returns "blocks"
        every { mongoTemplate.indexOps(IndexedBlock::class.java) } returns indexOperations
        every { indexOperations.indexInfo } returns emptyList()
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        BlocksCollectionConfig(
                mongoTemplate = mongoTemplate,
                indexerVersionService = indexerVersionService,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )
            .apply {
                initCollection()
                createPendingIndexes()
            }

        assertEquals(1, capturedIndexes.size)
        val index = capturedIndexes.single()
        assertTrue(index.indexKeys["blockNumber"] == -1)
        assertTrue(index.indexOptions["name"] == "blockNumber_-1")
    }
}
