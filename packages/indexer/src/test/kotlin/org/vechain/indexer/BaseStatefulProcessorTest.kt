package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.checkpoint.CheckpointService

@ExtendWith(MockKExtension::class)
class BaseStatefulProcessorTest {
    @MockK lateinit var repository: BaseIndexedRepository<TestDocument, String>

    @MockK lateinit var archiveService: ArchiveService<TestDocument>

    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var processor: TestableBaseStatefulProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        processor = TestableBaseStatefulProcessor(repository, archiveService, checkpointService)
    }

    @Test
    fun `rollback - saves checkpoint and rolls back archives`() {
        every { checkpointService.saveCheckpoint(TEST_COLLECTION, 10) } just Runs
        every { archiveService.rollback(10) } just Runs

        processor.rollback(10)

        verify(exactly = 1) { checkpointService.saveCheckpoint(TEST_COLLECTION, 10) }
        verify(exactly = 1) { archiveService.rollback(10) }
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
        archiveService: ArchiveService<*>,
        checkpointService: CheckpointService,
    ) :
        BaseStatefulProcessor(
            repository,
            archiveService,
            TEST_INDEXER_NAME,
            checkpointService,
            TEST_COLLECTION,
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
