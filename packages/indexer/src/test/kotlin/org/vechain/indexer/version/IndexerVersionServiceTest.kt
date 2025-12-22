package org.vechain.indexer.version

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.Optional
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.mapping.MongoMappingContext
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity
import strikt.api.expect
import strikt.assertions.isFalse

internal class IndexerVersionServiceTest {
    private lateinit var mongoTemplate: MongoTemplate
    private lateinit var repo: IndexerVersionRepository
    private lateinit var mappingContext: MongoMappingContext

    private lateinit var service: IndexerVersionService

    @BeforeEach
    fun setUp() {
        mongoTemplate = mockk(relaxed = true)
        repo = mockk(relaxed = true)
        mappingContext = mockk(relaxed = true)
        service = IndexerVersionService(mongoTemplate, repo, mappingContext)
    }

    @Test
    fun `checkAndResetCollectionIfVersionChanged - missing version doc does not drop collection`() {
        val clazz = Any::class.java
        val entity = mockk<MongoPersistentEntity<*>>()
        every { entity.collection } returns "test_collection"
        every { mappingContext.getPersistentEntity(clazz) } returns entity

        every { repo.findByCollectionName("test_collection") } returns null
        every { repo.findById(any()) } returns Optional.empty()
        every { repo.save(any<IndexerVersion>()) } answers { firstArg() }

        val dropped = service.checkAndResetCollectionIfVersionChanged("TestIndexer", clazz, 1)

        expect { that(dropped).isFalse() }
        verify(exactly = 0) { mongoTemplate.dropCollection("test_collection") }
        verify(exactly = 1) { repo.save(any()) }
    }
}
