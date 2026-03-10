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
class AverageFeesPerUserCollectionConfigTest {
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var indexOperations: IndexOperations
    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates a dayStartTimestamp index for day range reads`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(AverageFeesPerUser::class.java) } returns true
        every { mongoTemplate.indexOps(AverageFeesPerUser::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        AverageFeesPerUserCollectionConfig(
                mongoTemplate = mongoTemplate,
                indexerVersionService = indexerVersionService,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["dayStartTimestamp"] == 1 &&
                    it.indexOptions["name"] == "dayStartTimestamp_1"
            }
        )
    }
}
