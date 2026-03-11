package org.vechain.indexer.version

import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.index.IndexOperations

@ExtendWith(MockKExtension::class)
class IndexerVersionCollectionConfigTest {
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var indexOperations: IndexOperations

    @Test
    fun `ensureIndexes fails when unique index creation fails`() {
        every { mongoTemplate.indexOps(IndexerVersion::class.java) } returns indexOperations
        every { indexOperations.ensureIndex(any()) } throws RuntimeException("boom")

        val error =
            assertThrows(IllegalStateException::class.java) {
                IndexerVersionCollectionConfig(mongoTemplate).ensureIndexes()
            }

        assertEquals(
            "Failed to create index collectionName_1_unique for IndexerVersion",
            error.message,
        )
    }
}
