package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.model.BlockIdentifier

@ExtendWith(MockKExtension::class)
class BaseProcessorTest {

    @MockK lateinit var repository: BaseIndexedRepository<Document, String>

    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var processor: TestableBaseProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        processor = TestableBaseProcessor(repository, checkpointService)
    }

    @Test
    fun `rollback - saves checkpoint and deletes blocks starting from requested height`() {
        every { checkpointService.saveCheckpoint(TEST_COLLECTION, 10) } just Runs
        every { repository.deleteAllByBlockNumberGreaterThanEqual(10) } just Runs

        processor.rollback(10)

        verify { checkpointService.saveCheckpoint(TEST_COLLECTION, 10) }
        verify { repository.deleteAllByBlockNumberGreaterThanEqual(10) }
    }

    @Test
    fun `getLastSyncedBlock returns null when both checkpoint and repository are empty`() {
        every { checkpointService.getCheckpoint(TEST_COLLECTION) } returns null
        every { repository.getLatestRecord() } returns null

        val result = processor.getLastSyncedBlock()

        assertNull(result)
    }

    @Test
    fun `getLastSyncedBlock returns repository block when no checkpoint`() {
        val document = Document(blockId = "repo", blockNumber = 42L, blockTimestamp = 0L)
        every { checkpointService.getCheckpoint(TEST_COLLECTION) } returns null
        every { repository.getLatestRecord() } returns document

        val result = processor.getLastSyncedBlock()

        assertEquals("repo", result?.id)
        assertEquals(42L, result?.number)
    }

    @Test
    fun `getLastSyncedBlock returns checkpoint when it is ahead of repository`() {
        val document = Document(blockId = "repo", blockNumber = 42L, blockTimestamp = 0L)
        val checkpoint = BlockIdentifier(number = 100L, id = "")
        every { checkpointService.getCheckpoint(TEST_COLLECTION) } returns checkpoint
        every { repository.getLatestRecord() } returns document

        val result = processor.getLastSyncedBlock()

        assertEquals("", result?.id)
        assertEquals(100L, result?.number)
    }

    @Test
    fun `getLastSyncedBlock returns repository block when it is ahead of checkpoint`() {
        val document = Document(blockId = "repo", blockNumber = 200L, blockTimestamp = 0L)
        val checkpoint = BlockIdentifier(number = 100L, id = "")
        every { checkpointService.getCheckpoint(TEST_COLLECTION) } returns checkpoint
        every { repository.getLatestRecord() } returns document

        val result = processor.getLastSyncedBlock()

        assertEquals("repo", result?.id)
        assertEquals(200L, result?.number)
    }

    data class Document(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
    ) : IndexedDocument

    class TestableBaseProcessor(
        repository: BaseIndexedRepository<*, *>,
        checkpointService: CheckpointService,
    ) : BaseProcessor(repository, TEST_INDEXER_NAME, checkpointService, TEST_COLLECTION) {

        override suspend fun processEntry(entry: IndexingResult) {
            // no-op for tests
        }
    }

    companion object {
        private const val TEST_INDEXER_NAME = "TestIndexer"
        private const val TEST_COLLECTION = "test_collection"
    }
}
