package org.vechain.indexer.version

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.thor.model.BlockIdentifier
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNull
import strikt.assertions.isTrue

@Document("test_collection") private class DummyModel

@ExtendWith(MockKExtension::class)
class IndexerVersionServiceTest {
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var mappingContext: MongoMappingContext
    @MockK lateinit var repo: IndexerVersionRepository

    private lateinit var persistentEntity: MongoPersistentEntity<*>
    private lateinit var service: IndexerVersionService

    @BeforeEach
    fun setUp() {
        persistentEntity = mockk()
        every { persistentEntity.collection } returns "test_collection"
        every { mappingContext.getPersistentEntity(DummyModel::class.java) } returns
            persistentEntity

        service = IndexerVersionService(mongoTemplate, repo, mappingContext)
    }

    @Nested
    inner class CheckAndResetCollectionIfVersionChanged {
        @Test
        fun `drops collection and updates stored version when version increases`() {
            val block = BlockIdentifier(123, "0xabc")
            val existing =
                IndexerVersion(
                    indexerName = "testCollection",
                    collectionName = "test_collection",
                    version = 1,
                    lastProcessedBlock = block,
                )
            val saved = slot<IndexerVersion>()

            every { repo.findByCollectionName("test_collection") } returns existing
            every { repo.findByIdOrNull("testCollection") } returns existing
            every { mongoTemplate.dropCollection("test_collection") } just Runs
            every { repo.save(capture(saved)) } answers { saved.captured }

            val updated =
                service.checkAndResetCollectionIfVersionChanged(
                    "testCollection",
                    DummyModel::class.java,
                    2,
                )

            expectThat(updated).isTrue()
            expectThat(saved.captured.version).isEqualTo(2)
            expectThat(saved.captured.lastProcessedBlock).isEqualTo(block)

            verify(exactly = 1) { repo.findByCollectionName("test_collection") }
            verify(exactly = 1) { repo.findByIdOrNull("testCollection") }
            verify(exactly = 1) { mongoTemplate.dropCollection("test_collection") }
            verify(exactly = 1) { repo.save(any()) }
        }

        @Test
        fun `returns false when version unchanged`() {
            every { repo.findByCollectionName("test_collection") } returns
                IndexerVersion("testCollection", "test_collection", 2)

            val updated =
                service.checkAndResetCollectionIfVersionChanged(
                    "testCollection",
                    DummyModel::class.java,
                    2,
                )

            expectThat(updated).isFalse()
            verify(exactly = 1) { repo.findByCollectionName("test_collection") }
            verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
            verify(exactly = 0) { repo.save(any()) }
        }

        @Test
        fun `creates new version document when none exists`() {
            val saved = slot<IndexerVersion>()
            every { repo.findByCollectionName("test_collection") } returns null
            every { repo.findByIdOrNull("testCollection") } returns null
            every { repo.save(capture(saved)) } answers { saved.captured }

            val updated =
                service.checkAndResetCollectionIfVersionChanged(
                    "testCollection",
                    DummyModel::class.java,
                    1,
                )

            expectThat(updated).isTrue()
            expectThat(saved.captured.indexerName).isEqualTo("testCollection")
            expectThat(saved.captured.collectionName).isEqualTo("test_collection")
            expectThat(saved.captured.version).isEqualTo(1)
            verify(exactly = 1) { repo.findByCollectionName("test_collection") }
            verify(exactly = 1) { repo.findByIdOrNull("testCollection") }
            verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
            verify(exactly = 1) { repo.save(any()) }
        }

        @Test
        fun `returns false when collection mapping missing`() {
            every { mappingContext.getPersistentEntity(DummyModel::class.java) } returns null

            val updated =
                service.checkAndResetCollectionIfVersionChanged(
                    "testCollection",
                    DummyModel::class.java,
                    2,
                )

            expectThat(updated).isFalse()
            verify(exactly = 1) { mappingContext.getPersistentEntity(DummyModel::class.java) }
            verify(exactly = 0) { repo.findByCollectionName(any()) }
            verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
            verify(exactly = 0) { repo.save(any()) }
        }

        @Test
        fun `returns false when repository throws`() {
            every { repo.findByCollectionName("test_collection") } throws RuntimeException("boom")

            val updated =
                service.checkAndResetCollectionIfVersionChanged(
                    "testCollection",
                    DummyModel::class.java,
                    2,
                )

            expectThat(updated).isFalse()
            verify(exactly = 1) { repo.findByCollectionName("test_collection") }
            verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
            verify(exactly = 0) { repo.save(any()) }
        }
    }

    @Nested
    inner class DropArchiveCollection {
        @Test
        fun `drops requested archive collection`() {
            every { mongoTemplate.dropCollection("test_collection") } just Runs

            service.dropArchiveCollection(DummyModel::class.java)

            verify(exactly = 1) { mongoTemplate.dropCollection("test_collection") }
        }

        @Test
        fun `swallows exceptions when drop fails`() {
            every { mongoTemplate.dropCollection("test_collection") } throws
                RuntimeException("boom")

            val result = runCatching { service.dropArchiveCollection(DummyModel::class.java) }

            expectThat(result.isSuccess).isTrue()
            verify(exactly = 1) { mongoTemplate.dropCollection("test_collection") }
        }
    }

    @Nested
    inner class StoredIndexerVersion {
        @Test
        fun `returns stored version when present`() {
            every { repo.findByCollectionName("test_collection") } returns
                IndexerVersion("testCollection", "test_collection", 5)

            val version = service.getStoredIndexerVersion("test_collection")

            expectThat(version).isEqualTo(5)
            verify(exactly = 1) { repo.findByCollectionName("test_collection") }
        }

        @Test
        fun `returns null when version missing`() {
            every { repo.findByCollectionName("missing") } returns null

            val version = service.getStoredIndexerVersion("missing")

            expectThat(version).isNull()
            verify(exactly = 1) { repo.findByCollectionName("missing") }
        }
    }

    @Nested
    inner class LastProcessedBlock {
        @Test
        fun `returns stored block when present`() {
            val block = BlockIdentifier(100, "0xaaa")
            every { repo.findByIdOrNull("testCollection") } returns
                IndexerVersion(
                    indexerName = "testCollection",
                    collectionName = "test_collection",
                    version = 1,
                    lastProcessedBlock = block,
                )

            val storedBlock = service.getLastProcessedBlock("testCollection")

            expectThat(storedBlock).isEqualTo(block)
            verify(exactly = 1) { repo.findByIdOrNull("testCollection") }
        }

        @Test
        fun `returns null when indexer missing`() {
            every { repo.findByIdOrNull("missing") } returns null

            val storedBlock = service.getLastProcessedBlock("missing")

            expectThat(storedBlock).isNull()
            verify(exactly = 1) { repo.findByIdOrNull("missing") }
        }
    }

    @Nested
    inner class UpdateLastSafeSyncedBlock {
        @Test
        fun `updates last processed block when indexer exists`() {
            val newBlock = BlockIdentifier(200, "0xbb")
            val existing =
                IndexerVersion(
                    indexerName = "testCollection",
                    collectionName = "test_collection",
                    version = 3,
                    lastProcessedBlock = BlockIdentifier(199, "0xprev"),
                )
            val saved = slot<IndexerVersion>()
            every { repo.findByIdOrNull("testCollection") } returns existing
            every { repo.save(capture(saved)) } answers { saved.captured }

            service.updateLastSafeSyncedBlock("testCollection", newBlock)

            expectThat(saved.captured.lastProcessedBlock).isEqualTo(newBlock)
            expectThat(saved.captured.version).isEqualTo(existing.version)
            verify(exactly = 1) { repo.findByIdOrNull("testCollection") }
            verify(exactly = 1) { repo.save(any()) }
        }

        @Test
        fun `does not save when indexer missing`() {
            every { repo.findByIdOrNull("missing") } returns null

            service.updateLastSafeSyncedBlock("missing", BlockIdentifier(1, "0x1"))

            verify(exactly = 1) { repo.findByIdOrNull("missing") }
            verify(exactly = 0) { repo.save(any()) }
        }

        @Test
        fun `returns early when block is null`() {
            service.updateLastSafeSyncedBlock("testCollection", null)

            verify(exactly = 0) { repo.findByIdOrNull(any()) }
            verify(exactly = 0) { repo.save(any()) }
        }
    }
}
