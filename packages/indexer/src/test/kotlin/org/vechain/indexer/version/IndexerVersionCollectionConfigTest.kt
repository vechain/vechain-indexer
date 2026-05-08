package org.vechain.indexer.version

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexDefinition
import org.springframework.data.mongodb.core.index.IndexOperations

@ExtendWith(MockKExtension::class)
class IndexerVersionCollectionConfigTest {
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var indexOperations: IndexOperations

    @Test
    fun `initCollection registers unique collectionName index without partial filter`() {
        val captured = slot<IndexDefinition>()
        every { mongoTemplate.collectionExists(IndexerVersion::class.java) } returns true
        every { mongoTemplate.getCollectionName(IndexerVersion::class.java) } returns
            "indexer_versions"
        every { mongoTemplate.indexOps(IndexerVersion::class.java) } returns indexOperations
        every { mongoTemplate.indexOps("indexer_versions") } returns indexOperations
        every { indexOperations.indexInfo } returns emptyList()
        every { indexOperations.createIndex(capture(captured)) } returns "created"

        IndexerVersionCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )
            .apply {
                initCollection()
                removeStaleIndexes()
                createPendingIndexes()
            }

        verify(exactly = 1) { indexOperations.createIndex(any()) }
        assertEquals("collectionName_1_unique", captured.captured.indexOptions["name"])
        assertEquals(1, captured.captured.indexKeys["collectionName"])
        assertEquals(true, captured.captured.indexOptions["unique"])
        // Unlike IndexedDocument-backed collections, IndexerVersion has no blockNumber field, so
        // the partial-filter default must be suppressed.
        assertNull(captured.captured.indexOptions["partialFilterExpression"])
    }
}
