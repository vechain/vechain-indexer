package org.vechain.indexer.version

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class IndexerVersionServiceTest {
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var repo: IndexerVersionRepository
    private lateinit var mappingContext: MongoMappingContext

    private lateinit var service: IndexerVersionService

    private fun stubCollectionName(clazz: Class<*>, collectionName: String) {
        val entity = mockk<MongoPersistentEntity<*>>()
        every { entity.collection } returns collectionName
        every { mappingContext.getPersistentEntity(clazz) } returns entity
    }

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk(relaxed = true)
        repo = mockk(relaxed = true)
        mappingContext = mockk(relaxed = true)
        service = IndexerVersionService(mongoTemplate, repo, mappingContext)
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - missing version doc does not drop collection`() {
        val indexerName = "TestIndexer"
        val clazz = Any::class.java
        stubCollectionName(clazz, "test_collection")

        every { repo.findByCollectionName("test_collection") } returns null
        every { repo.findById(any()) } returns Optional.empty()
        every { repo.save(any<IndexerVersion>()) } answers { firstArg() }

        val dropped = service.checkAndResetCollectionIfVersionChanged(indexerName, clazz, 1)

        expect { that(dropped).isFalse() }
        verify(exactly = 0) { mongoTemplate.dropCollection("test_collection") }
        verify(exactly = 1) { repo.save(any()) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - stored version lower drops and updates`() {
        val indexerName = "TestIndexer"
        val clazz = Any::class.java
        stubCollectionName(clazz, "test_collection")

        every { repo.findByCollectionName("test_collection") } returns
            IndexerVersion(
                indexerName = indexerName,
                collectionName = "test_collection",
                version = 1,
            )
        every { repo.findById(indexerName) } returns
            Optional.of(
                IndexerVersion(
                    indexerName = indexerName,
                    collectionName = "test_collection",
                    version = 1,
                )
            )

        val saved = slot<IndexerVersion>()
        every { repo.save(capture(saved)) } answers { firstArg() }
        every { mongoTemplate.dropCollection("test_collection") } returns Unit

        val dropped = service.checkAndResetCollectionIfVersionChanged(indexerName, clazz, 2)

        expect {
            that(dropped).isTrue()
            that(saved.captured.indexerName).isEqualTo(indexerName)
            that(saved.captured.collectionName).isEqualTo("test_collection")
            that(saved.captured.version).isEqualTo(2)
        }
        verify(exactly = 1) { mongoTemplate.dropCollection("test_collection") }
        verify(exactly = 1) { repo.save(any()) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - drop throws returns false`() {
        val indexerName = "TestIndexer"
        val clazz = Any::class.java
        stubCollectionName(clazz, "test_collection")

        every { repo.findByCollectionName("test_collection") } returns
            IndexerVersion(
                indexerName = indexerName,
                collectionName = "test_collection",
                version = 1,
            )
        every { repo.findById(indexerName) } returns Optional.empty()
        every { repo.save(any<IndexerVersion>()) } answers { firstArg() }
        every { mongoTemplate.dropCollection("test_collection") } throws RuntimeException("boom")

        val dropped = service.checkAndResetCollectionIfVersionChanged(indexerName, clazz, 2)

        expect { that(dropped).isFalse() }
        verify(exactly = 1) { repo.save(any()) }
        verify(exactly = 1) { mongoTemplate.dropCollection("test_collection") }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - stored version equal does nothing`() {
        val indexerName = "TestIndexer"
        val clazz = Any::class.java
        stubCollectionName(clazz, "test_collection")

        every { repo.findByCollectionName("test_collection") } returns
            IndexerVersion(
                indexerName = indexerName,
                collectionName = "test_collection",
                version = 2,
            )

        val dropped = service.checkAndResetCollectionIfVersionChanged(indexerName, clazz, 2)

        expect { that(dropped).isFalse() }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - stored version higher does nothing`() {
        val indexerName = "TestIndexer"
        val clazz = Any::class.java
        stubCollectionName(clazz, "test_collection")

        every { repo.findByCollectionName("test_collection") } returns
            IndexerVersion(
                indexerName = indexerName,
                collectionName = "test_collection",
                version = 3,
            )

        val dropped = service.checkAndResetCollectionIfVersionChanged(indexerName, clazz, 2)

        expect { that(dropped).isFalse() }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - exception returns false`() {
        val indexerName = "TestIndexer"
        val clazz = Any::class.java
        stubCollectionName(clazz, "test_collection")

        every { repo.findByCollectionName("test_collection") } throws RuntimeException("boom")

        val dropped = service.checkAndResetCollectionIfVersionChanged(indexerName, clazz, 1)

        expect { that(dropped).isFalse() }
        verify(exactly = 0) { repo.save(any()) }
        verify(exactly = 0) { mongoTemplate.dropCollection(any<String>()) }
    }
}
