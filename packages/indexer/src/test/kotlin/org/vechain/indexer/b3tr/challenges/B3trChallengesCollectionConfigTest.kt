package org.vechain.indexer.b3tr.challenges

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
class B3trChallengesCollectionConfigTest {
    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates list and rollback indexes`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(B3trChallenge::class.java) } returns true
        every { mongoTemplate.getCollectionName(B3trChallenge::class.java) } returns
            "b3tr_challenges"
        every { mongoTemplate.indexOps(B3trChallenge::class.java) } returns indexOperations
        every { indexOperations.createIndex(capture(capturedIndexes)) } returns "created"

        B3trChallengesCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexOptions["name"] == "createdAtBlockTimestamp_-1_challengeId_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexOptions["name"] ==
                    "visibility_1_status_1_createdAtBlockTimestamp_-1_challengeId_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["blockNumber"] == -1 && it.indexOptions["name"] == "blockNumber_-1"
            }
        )
        assertTrue(
            capturedIndexes.any {
                it.indexKeys["declined"] == 1 && it.indexOptions["name"] == "declined_1"
            }
        )
    }
}
