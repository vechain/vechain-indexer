package org.vechain.indexer.transaction

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
class TransactionCollectionConfigTest {

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var indexOperations: IndexOperations

    @MockK lateinit var indexerVersionService: IndexerVersionService

    @Test
    fun `initCollection creates latest transactions canonical index`() {
        val capturedIndexes = mutableListOf<IndexDefinition>()
        every {
            indexerVersionService.checkAndResetCollectionIfVersionChanged(any(), any(), any())
        } returns false
        every { mongoTemplate.collectionExists(IndexedTransaction::class.java) } returns true
        every { mongoTemplate.getCollectionName(IndexedTransaction::class.java) } returns
            "transactions"
        every { mongoTemplate.indexOps(IndexedTransaction::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(capture(capturedIndexes)) } returns "created"

        TransactionCollectionConfig(
                mongoTemplate = mongoTemplate,
                appCoroutineScope = CoroutineScope(Dispatchers.Unconfined),
                indexerVersionService = indexerVersionService,
            )
            .initCollection()

        assertTrue(
            capturedIndexes.any {
                it.indexOptions["name"] == "blockNumber_-1_transactionIndex_1" &&
                    it.indexKeys["blockNumber"] == -1 &&
                    it.indexKeys["transactionIndex"] == 1
            }
        )
    }
}
