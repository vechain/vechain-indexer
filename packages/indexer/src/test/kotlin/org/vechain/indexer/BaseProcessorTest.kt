package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.thor.model.BlockIdentifier

@ExtendWith(MockKExtension::class)
class BaseProcessorTest {

    @MockK lateinit var repository: BaseIndexedRepository<Document, String>

    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var processor: TestableBaseProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        processor = TestableBaseProcessor(repository, checkpointService, mockk(relaxed = true))
    }

    @Test
    fun `rollback - saves checkpoint and deletes blocks starting from requested height`() {
        every { checkpointService.saveCheckpoint(TEST_COLLECTION, 9) } just Runs
        every { repository.deleteAllByBlockNumberGreaterThanEqual(10) } just Runs

        processor.rollback(10)

        verify { checkpointService.saveCheckpoint(TEST_COLLECTION, 9) }
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
        processorMetrics: ProcessorMetrics,
    ) :
        BaseProcessor(
            repository,
            TEST_INDEXER_NAME,
            checkpointService,
            TEST_COLLECTION,
            processorMetrics,
        ) {

        override suspend fun processEntry(entry: IndexingResult) {
            // no-op for tests
        }
    }

    class ThrowingBaseProcessor(
        repository: BaseIndexedRepository<*, *>,
        checkpointService: CheckpointService,
        processorMetrics: ProcessorMetrics,
    ) :
        BaseProcessor(
            repository,
            TEST_INDEXER_NAME,
            checkpointService,
            TEST_COLLECTION,
            processorMetrics,
        ) {

        override suspend fun processEntry(entry: IndexingResult) {
            throw RuntimeException("processEntry failed")
        }
    }

    @Nested
    inner class CheckpointSaving {

        @BeforeEach
        fun setup() {
            every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
        }

        private fun block(number: Long): Block {
            val block = mockk<Block>()
            every { block.number } returns number
            return block
        }

        @Test
        fun `process saves checkpoint with block number for BlockResult`() = runBlocking {
            processor.process(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING)
            )

            verify { checkpointService.trySaveCheckpoint(TEST_COLLECTION, 100) }
        }

        @Test
        fun `process saves checkpoint with endBlock for LogResult`() = runBlocking {
            processor.process(IndexingResult.LogResult(250, emptyList(), Status.SYNCING))

            verify { checkpointService.trySaveCheckpoint(TEST_COLLECTION, 250) }
        }

        @Test
        fun `process does not save checkpoint if processEntry throws`() {
            val throwingProcessor =
                ThrowingBaseProcessor(repository, checkpointService, mockk(relaxed = true))

            assertThrows<RuntimeException> {
                runBlocking {
                    throwingProcessor.process(
                        IndexingResult.BlockResult(
                            block(100),
                            emptyList(),
                            emptyList(),
                            Status.SYNCING,
                        )
                    )
                }
            }

            verify(exactly = 0) { checkpointService.trySaveCheckpoint(any(), any()) }
        }
    }

    @Nested
    inner class EventOrderingPrecondition {

        @BeforeEach
        fun setup() {
            every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
        }

        private fun event(blockNumber: Long, id: String = "evt-$blockNumber") =
            buildIndexedEvent(
                id = id,
                blockId = "block-$blockNumber",
                blockNumber = blockNumber,
                blockTimestamp = blockNumber * 10,
                eventType = "Transfer",
                params = AbiEventParameters(returnValues = emptyMap()),
            )

        @Test
        fun `process accepts events in non-decreasing block order`() = runBlocking {
            processor.process(
                IndexingResult.LogResult(
                    3L,
                    listOf(event(1L), event(1L), event(2L), event(3L)),
                    Status.SYNCING,
                )
            )

            verify { checkpointService.trySaveCheckpoint(TEST_COLLECTION, 3L) }
        }

        @Test
        fun `process throws when a later event has a lower block number`() {
            val outOfOrder =
                IndexingResult.LogResult(2L, listOf(event(2L), event(1L)), Status.SYNCING)

            val ex =
                assertThrows<IllegalStateException> {
                    runBlocking { processor.process(outOfOrder) }
                }

            assertEquals(true, ex.message?.contains(TEST_INDEXER_NAME))
            assertEquals(true, ex.message?.contains("out-of-order"))
            verify(exactly = 0) { checkpointService.trySaveCheckpoint(any(), any()) }
        }

        @Test
        fun `process accepts an empty event list`() = runBlocking {
            processor.process(IndexingResult.LogResult(5L, emptyList(), Status.SYNCING))
            verify { checkpointService.trySaveCheckpoint(TEST_COLLECTION, 5L) }
        }
    }

    companion object {
        private const val TEST_INDEXER_NAME = "TestIndexer"
        private const val TEST_COLLECTION = "test_collection"
    }
}
