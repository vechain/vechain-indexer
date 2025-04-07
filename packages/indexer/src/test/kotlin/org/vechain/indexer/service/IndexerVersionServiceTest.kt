package org.vechain.indexer.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.model.IndexerVersion
import strikt.api.expect
import strikt.assertions.isEqualTo

class IndexerVersionServiceTest {
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var indexerVersionService: IndexerVersionService

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk() // Create a mock of MongoTemplate
        indexerVersionService = IndexerVersionService(mongoTemplate)
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - should drop collection and update version if version has changed`() {
        val collectionName = "testCollection"
        val newVersion = 2

        every { mongoTemplate.findById(collectionName, IndexerVersion::class.java) } returns
            IndexerVersion(collectionName, 1)

        every { mongoTemplate.dropCollection(collectionName) } just Runs

        every { mongoTemplate.save(any<IndexerVersion>()) } returns
            IndexerVersion(collectionName, newVersion)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                collectionName,
                newVersion
            )

        verify { mongoTemplate.dropCollection(collectionName) }
        verify { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(true) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - should not drop collection if version is not changed`() {
        val collectionName = "testCollection"
        val newVersion = 1

        every { mongoTemplate.findById(collectionName, IndexerVersion::class.java) } returns
            IndexerVersion(collectionName, newVersion)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                collectionName,
                newVersion
            )

        verify(exactly = 0) { mongoTemplate.dropCollection(collectionName) }
        verify(exactly = 0) { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(false) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - should create version document if no version document found`() {
        val collectionName = "testCollection"
        val newVersion = 1

        every { mongoTemplate.findById(collectionName, IndexerVersion::class.java) } returns null

        every { mongoTemplate.dropCollection(collectionName) } just Runs

        every { mongoTemplate.save(any<IndexerVersion>()) } returns
            IndexerVersion(collectionName, newVersion)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                collectionName,
                newVersion
            )

        verify(exactly = 0) { mongoTemplate.dropCollection(collectionName) }

        verify { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(false) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - should handle error when exception is thrown`() {
        val collectionName = "testCollection"
        val newVersion = 2

        every { mongoTemplate.findById(collectionName, IndexerVersion::class.java) } throws
            RuntimeException("Error fetching version")

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                collectionName,
                newVersion
            )

        expect { that(result).isEqualTo(false) }
    }
}
