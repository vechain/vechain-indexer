package org.vechain.indexer.service

import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.Document
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.vechain.indexer.model.IndexerVersion
import strikt.api.expect
import strikt.assertions.isEqualTo

@Document("testCollection") class DummyModel

class IndexerVersionServiceTest {
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var mongoMappingContext: MongoMappingContext
    private lateinit var indexerVersionService: IndexerVersionService

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk()
        mongoMappingContext = mockk()

        every {
            mongoMappingContext.getPersistentEntity(DummyModel::class.java)?.collection
        } returns "testCollection"

        indexerVersionService = IndexerVersionService(mongoTemplate, mongoMappingContext)
    }

    @Test
    fun `should drop collection and update version if version has changed`() {
        every { mongoTemplate.findById("testCollection", IndexerVersion::class.java) } returns
            IndexerVersion("testCollection", 1)
        every { mongoTemplate.dropCollection("testCollection") } just Runs
        every { mongoTemplate.save(any<IndexerVersion>()) } returns
            IndexerVersion("testCollection", 2)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(DummyModel::class.java, 2)

        verify { mongoTemplate.dropCollection("testCollection") }
        verify { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(true) }
    }

    @Test
    fun `should not drop collection if version is not changed`() {
        every { mongoTemplate.findById("testCollection", IndexerVersion::class.java) } returns
            IndexerVersion("testCollection", 1)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(DummyModel::class.java, 1)

        verify(exactly = 0) { mongoTemplate.dropCollection("testCollection") }
        verify(exactly = 0) { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(false) }
    }

    @Test
    fun `should create version document if no version document found`() {
        every { mongoTemplate.findById("testCollection", IndexerVersion::class.java) } returns null
        every { mongoTemplate.save(any<IndexerVersion>()) } returns
            IndexerVersion("testCollection", 1)

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(DummyModel::class.java, 1)

        verify(exactly = 0) { mongoTemplate.dropCollection("testCollection") }
        verify { mongoTemplate.save(any<IndexerVersion>()) }

        expect { that(result).isEqualTo(false) }
    }

    @Test
    fun `should handle error when exception is thrown`() {
        every { mongoTemplate.findById("testCollection", IndexerVersion::class.java) } throws
            RuntimeException("Error")

        val result =
            indexerVersionService.checkAndResetCollectionIfVersionChanged(DummyModel::class.java, 2)

        expect { that(result).isEqualTo(false) }
    }
}
