package org.vechain.indexer.version

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.thor.model.BlockIdentifier
import strikt.api.expect
import strikt.assertions.isEqualTo

@Document("test_collection") class DummyModel

@ExtendWith(MockKExtension::class)
class IndexerVersionServiceTest {
    private lateinit var mongoTemplate: MongoTemplate
    @MockK private lateinit var mongoMappingContext: MongoMappingContext
    @MockK lateinit var indexerVersionRepository: IndexerVersionRepository
    private lateinit var indexerVersionService: IndexerVersionService

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk()
        mongoMappingContext = mockk()

        every {
            mongoMappingContext.getPersistentEntity(DummyModel::class.java)?.collection
        } returns "test_collection"

        indexerVersionService =
            IndexerVersionService(mongoTemplate, indexerVersionRepository, mongoMappingContext)
    }

    @Test
    fun `should drop collection and update version if version has changed`() {
        every { indexerVersionRepository.findByIdOrNull("testCollection") } returns null
        every { indexerVersionRepository.findByCollectionName("test_collection") } returns
            IndexerVersion("testCollection", "test_collection", 1)
        every { mongoTemplate.dropCollection("test_collection") } just Runs
        every { mongoTemplate.save(any<IndexerVersion>()) } returns
            IndexerVersion("testCollection", "test_collection", 2)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                "testCollection",
                DummyModel::class.java,
                2,
            )

        verify { mongoTemplate.dropCollection("test_collection") }
        verify { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(true) }
    }

    @Test
    fun `should not drop collection if version is not changed`() {
        every { indexerVersionRepository.findByCollectionName("test_collection") } returns
            IndexerVersion("testCollection", "test_collection", 1)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                "testCollection",
                DummyModel::class.java,
                1,
            )

        verify(exactly = 0) { mongoTemplate.dropCollection("test_collection") }
        verify(exactly = 0) { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(false) }
    }

    @Test
    fun `should create version document if no version document found`() {
        every { indexerVersionRepository.findByIdOrNull("testCollection") } returns null
        every { indexerVersionRepository.findByCollectionName("test_collection") } returns null
        every { mongoTemplate.save(any<IndexerVersion>()) } returns
            IndexerVersion("testCollection", "test_collection", 1)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                "testCollection",
                DummyModel::class.java,
                1,
            )

        verify(exactly = 0) { mongoTemplate.dropCollection("test_collection") }
        verify { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(true) }
    }

    @Test
    fun `should handle error when exception is thrown`() {
        every { indexerVersionRepository.findByCollectionName("test_collection") } throws
            RuntimeException("Error")

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(
                "testCollection",
                DummyModel::class.java,
                2,
            )

        expect { that(result).isEqualTo(false) }
    }

    @Nested
    inner class UpdateLastSafeSyncedBlock {
        @Test
        fun `should update last safe synced block if indexer exists`() {
            val blockIdentifier = BlockIdentifier(100, "0xabc")
            val existingIndexerVersion =
                IndexerVersion(
                    indexerName = "testCollection",
                    collectionName = "test_collection",
                    version = 1,
                )
            val expectedResult =
                IndexerVersion(
                    indexerName = "testCollection",
                    collectionName = "test_collection",
                    version = 1,
                    lastProcessedBlock = blockIdentifier,
                )
            every { indexerVersionRepository.findByIdOrNull("testCollection") } returns
                existingIndexerVersion
            every { indexerVersionRepository.save(expectedResult) } returns expectedResult

            indexerVersionService.updateLastSafeSyncedBlock("testCollection", blockIdentifier)

            verify(exactly = 1) { indexerVersionRepository.save(expectedResult) }
        }

        @Test
        fun `should not update last safe synced block if indexer does not exist`() {
            val blockIdentifier = BlockIdentifier(100, "0xabc")
            every { indexerVersionRepository.findByIdOrNull("testCollection") } returns null

            indexerVersionService.updateLastSafeSyncedBlock("testCollection", blockIdentifier)

            verify(exactly = 0) { indexerVersionRepository.save(any()) }
        }

        @Test
        fun `if block is null shouldn't save anything`() {
            indexerVersionService.updateLastSafeSyncedBlock("testCollection", null)

            verify(exactly = 0) { indexerVersionRepository.save(any()) }
        }
    }
}
