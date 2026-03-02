package org.vechain.indexer

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
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
    inner class ProcessingDurationMetrics {

        private lateinit var meterRegistry: SimpleMeterRegistry
        private lateinit var metricsProcessor: TestableBaseProcessor

        @BeforeEach
        fun setup() {
            meterRegistry = SimpleMeterRegistry()
            every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
            metricsProcessor =
                TestableBaseProcessor(
                    repository,
                    checkpointService,
                    ProcessorMetrics(meterRegistry),
                )
        }

        private fun block(number: Long): Block {
            val block = mockk<Block>()
            every { block.number } returns number
            return block
        }

        @Test
        fun `BlockResult always records exactly one observation per call`() = runBlocking {
            // Process block 100, then block 200 — BlockResult should always be 1 block
            metricsProcessor.process(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING)
            )
            metricsProcessor.process(
                IndexingResult.BlockResult(block(200), emptyList(), emptyList(), Status.SYNCING)
            )

            val timer =
                meterRegistry
                    .find("processor_duration")
                    .tag("indexer_name", TEST_INDEXER_NAME)
                    .timer()!!

            assertEquals(2L, timer.count())
        }

        @Test
        fun `LogResult computes block count from delta`() = runBlocking {
            metricsProcessor.process(IndexingResult.LogResult(100, emptyList(), Status.SYNCING))
            metricsProcessor.process(IndexingResult.LogResult(110, emptyList(), Status.SYNCING))

            val timer =
                meterRegistry
                    .find("processor_duration")
                    .tag("indexer_name", TEST_INDEXER_NAME)
                    .timer()!!

            // First call: blocksInEntry=1 (no previous), second call: blocksInEntry=10
            assertEquals(11L, timer.count())
        }

        @Test
        fun `rollback resets lastProcessedBlock so next entry uses blocksInEntry of 1`() =
            runBlocking {
                every { checkpointService.saveCheckpoint(TEST_COLLECTION, 99) } just Runs
                every { repository.deleteAllByBlockNumberGreaterThanEqual(100) } just Runs

                // Process to block 200
                metricsProcessor.process(IndexingResult.LogResult(200, emptyList(), Status.SYNCING))

                // Rollback to block 100
                metricsProcessor.rollback(100)

                // Process block 50 (below previous lastProcessedBlock of 200)
                metricsProcessor.process(IndexingResult.LogResult(50, emptyList(), Status.SYNCING))

                val timer =
                    meterRegistry
                        .find("processor_duration")
                        .tag("indexer_name", TEST_INDEXER_NAME)
                        .timer()!!

                // First call: 1, after rollback + second call: 1 (reset, not skewed)
                assertEquals(2L, timer.count())
            }
    }

    companion object {
        private const val TEST_INDEXER_NAME = "TestIndexer"
        private const val TEST_COLLECTION = "test_collection"
    }
}
