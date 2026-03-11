package org.vechain.indexer.b3tr.action.mongo

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
import org.vechain.indexer.b3tr.action.AppAllTimeActionSummary
import org.vechain.indexer.b3tr.action.AppDailyActionSummary
import org.vechain.indexer.b3tr.action.AppRoundActionSummary
import org.vechain.indexer.b3tr.action.UserDailyActionSummary
import org.vechain.indexer.b3tr.action.UserRoundActionSummary
import org.vechain.indexer.version.IndexerVersionService

@ExtendWith(MockKExtension::class)
class ActionSummaryCollectionConfigTest {

    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var indexOperations: IndexOperations
    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `app all-time config creates direct app-user lookup index`() {
        val capturedIndexes = captureIndexes(AppAllTimeActionSummary::class.java)

        AppAllTimeActionSummaryCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                version = 1,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["appId"] == 1 &&
                    it.indexKeys["user"] == 1 &&
                    it.indexOptions["name"] == "appId_1_user_1"
            }
        )
    }

    @Test
    fun `app daily config creates direct app-date-user lookup index`() {
        val capturedIndexes = captureIndexes(AppDailyActionSummary::class.java)

        AppDailyActionSummaryCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                version = 1,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["appId"] == 1 &&
                    it.indexKeys["date"] == 1 &&
                    it.indexKeys["user"] == 1 &&
                    it.indexOptions["name"] == "appId_1_date_1_user_1"
            }
        )
    }

    @Test
    fun `app round config creates direct app-round-user lookup index`() {
        val capturedIndexes = captureIndexes(AppRoundActionSummary::class.java)

        AppRoundActionSummaryCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                version = 1,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["appId"] == 1 &&
                    it.indexKeys["roundId"] == 1 &&
                    it.indexKeys["user"] == 1 &&
                    it.indexOptions["name"] == "appId_1_roundId_1_user_1"
            }
        )
    }

    @Test
    fun `user daily config creates direct entity-date lookup index`() {
        val capturedIndexes = captureIndexes(UserDailyActionSummary::class.java)

        UserDailyActionSummaryCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                version = 1,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["entity"] == 1 &&
                    it.indexKeys["date"] == 1 &&
                    it.indexOptions["name"] == "entity_1_date_1"
            }
        )
    }

    @Test
    fun `user round config creates direct entity-round lookup index`() {
        val capturedIndexes = captureIndexes(UserRoundActionSummary::class.java)

        UserRoundActionSummaryCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
                version = 1,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexKeys["entity"] == 1 &&
                    it.indexKeys["roundId"] == 1 &&
                    it.indexOptions["name"] == "entity_1_roundId_1"
            }
        )
    }

    private fun captureIndexes(entityClass: Class<*>): MutableList<IndexDefinition> {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(entityClass) } returns true
        every { mongoTemplate.indexOps(entityClass) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"
        return capturedIndexes
    }
}
