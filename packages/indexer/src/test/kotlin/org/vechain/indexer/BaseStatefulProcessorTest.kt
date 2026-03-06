package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics

@ExtendWith(MockKExtension::class)
class BaseStatefulProcessorTest {
    @MockK lateinit var repository: BaseIndexedRepository<TestDocument, String>

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var processor: TestableBaseStatefulProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        mockkObject(InlineVersionService)

        processor =
            TestableBaseStatefulProcessor(
                repository,
                mongoTemplate,
                checkpointService,
                mockk(relaxed = true),
            )
    }

    @AfterEach
    fun tearDown() {
        unmockkObject(InlineVersionService)
    }

    @Test
    fun `rollback - saves checkpoint and rolls back inline versions`() {
        every { checkpointService.saveCheckpoint(TEST_COLLECTION, 9) } just Runs
        every { InlineVersionService.rollback(TEST_COLLECTION, 10, mongoTemplate, 1) } just Runs

        processor.rollback(10)

        verify(exactly = 1) { checkpointService.saveCheckpoint(TEST_COLLECTION, 9) }
        verify(exactly = 1) { InlineVersionService.rollback(TEST_COLLECTION, 10, mongoTemplate, 1) }
    }

    @Test
    fun `rollback - uses standard initial version for all collections`() {
        val processor =
            TestableBaseStatefulProcessor(
                repository,
                mongoTemplate,
                checkpointService,
                mockk(relaxed = true),
                IndexerNames.ACCOUNT_OVERVIEW.COLLECTION,
            )

        every { checkpointService.saveCheckpoint(IndexerNames.ACCOUNT_OVERVIEW.COLLECTION, 9) } just
            Runs
        every {
            InlineVersionService.rollback(
                IndexerNames.ACCOUNT_OVERVIEW.COLLECTION,
                10,
                mongoTemplate,
                1,
            )
        } just Runs

        processor.rollback(10)

        verify(exactly = 1) {
            checkpointService.saveCheckpoint(IndexerNames.ACCOUNT_OVERVIEW.COLLECTION, 9)
        }
        verify(exactly = 1) {
            InlineVersionService.rollback(
                IndexerNames.ACCOUNT_OVERVIEW.COLLECTION,
                10,
                mongoTemplate,
                1,
            )
        }
    }

    data class TestDocument(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
        override val version: Int,
    ) : VersionedDocument {
        override fun getDocumentId(): String {
            return "$blockId-$blockNumber-$version"
        }
    }

    class TestableBaseStatefulProcessor(
        repository: BaseIndexedRepository<*, *>,
        mongoTemplate: MongoTemplate,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
        collectionName: String = TEST_COLLECTION,
    ) :
        BaseStatefulProcessor(
            repository,
            mongoTemplate,
            TEST_INDEXER_NAME,
            checkpointService,
            collectionName,
            processorMetrics,
        ) {

        override suspend fun processEntry(entry: IndexingResult) {
            // does nothing
        }
    }

    companion object {
        private const val TEST_INDEXER_NAME = "TestStatefulIndexer"
        private const val TEST_COLLECTION = "test_collection"
    }
}
