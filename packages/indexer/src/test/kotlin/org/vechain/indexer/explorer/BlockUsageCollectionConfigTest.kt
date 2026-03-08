package org.vechain.indexer.explorer

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
class BlockUsageCollectionConfigTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates blockNumber index for startup rollback`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(BlockUsage::class.java) } returns true
        every { mongoTemplate.indexOps(BlockUsage::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        BlockUsageCollectionConfig(
                mongoTemplate = mongoTemplate,
                indexerVersionService = indexerVersionService,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
    }
}
