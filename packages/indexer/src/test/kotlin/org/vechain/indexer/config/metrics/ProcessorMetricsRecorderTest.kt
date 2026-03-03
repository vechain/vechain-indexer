package org.vechain.indexer.config.metrics

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration.Companion.milliseconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.thor.model.Block

class ProcessorMetricsRecorderTest {

    private lateinit var registry: SimpleMeterRegistry
    private lateinit var metrics: ProcessorMetrics
    private lateinit var recorder: ProcessorMetricsRecorder

    @BeforeEach
    fun setup() {
        registry = SimpleMeterRegistry()
        metrics = ProcessorMetrics(registry)
        recorder = ProcessorMetricsRecorder(INDEXER_NAME, metrics)
    }

    private fun block(number: Long): Block {
        val block = mockk<Block>()
        every { block.number } returns number
        return block
    }

    @Test
    fun `recordEvents delegates to processorMetrics incrementEventsCounter`() {
        recorder.recordEvents(5)

        val counter =
            registry.find("processor_events_counter").tag("indexer_name", INDEXER_NAME).counter()

        assertEquals(5.0, counter!!.count())
    }

    @Nested
    inner class ProcessingDuration {

        @Test
        fun `BlockResult always records exactly one duration observation`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `LogResult first call records 1 observation (no previous block)`() {
            recorder.record(
                IndexingResult.LogResult(100, emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `LogResult subsequent call computes block count from delta`() {
            recorder.record(
                IndexingResult.LogResult(100, emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.LogResult(110, emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            // First call: 1, second call: 10 (110 - 100)
            assertEquals(11L, timer.count())
        }

        @Test
        fun `reset resets lastProcessedBlock so next LogResult uses blocksInEntry of 1`() {
            recorder.record(
                IndexingResult.LogResult(200, emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.reset()
            recorder.record(
                IndexingResult.LogResult(50, emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            // First call: 1, after reset + second call: 1
            assertEquals(2L, timer.count())
        }
    }

    @Nested
    inner class CycleTime {

        @Test
        fun `first call does not record cycle time`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_cycle_time").tag("indexer_name", INDEXER_NAME).timer()

            assertNull(timer)
        }

        @Test
        fun `second call records cycle time`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(101), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_cycle_time").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `records cycle time regardless of status`() {
            recorder.record(
                IndexingResult.BlockResult(
                    block(100),
                    emptyList(),
                    emptyList(),
                    Status.FULLY_SYNCED,
                ),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(
                    block(101),
                    emptyList(),
                    emptyList(),
                    Status.FULLY_SYNCED,
                ),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_cycle_time").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `records cycle time across status transitions`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(
                    block(101),
                    emptyList(),
                    emptyList(),
                    Status.FULLY_SYNCED,
                ),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(102), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_cycle_time").tag("indexer_name", INDEXER_NAME).timer()!!

            // Second and third calls each record cycle time
            assertEquals(2L, timer.count())
        }

        @Test
        fun `reset clears cycle time tracking`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(101), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            recorder.reset()

            // After reset, this should NOT record cycle time (first call after reset)
            recorder.record(
                IndexingResult.BlockResult(block(102), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_cycle_time").tag("indexer_name", INDEXER_NAME).timer()!!

            // Only the second call before reset should have recorded
            assertEquals(1L, timer.count())
        }
    }

    @Nested
    inner class RollbackGuard {

        @Test
        fun `LogResult with lower block number is skipped`() {
            recorder.record(
                IndexingResult.LogResult(100, emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.LogResult(50, emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `BlockResult with lower block number is skipped`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(50), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `same block number is skipped`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            assertEquals(1L, timer.count())
        }

        @Test
        fun `skipped call does not record cycle time`() {
            recorder.record(
                IndexingResult.BlockResult(block(100), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(101), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            recorder.record(
                IndexingResult.BlockResult(block(50), emptyList(), emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_cycle_time").tag("indexer_name", INDEXER_NAME).timer()!!

            // Only block 101 recorded cycle time; block 50 was skipped
            assertEquals(1L, timer.count())
        }

        @Test
        fun `forward progress resumes after skipped call`() {
            recorder.record(
                IndexingResult.LogResult(100, emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            // Skipped — going backwards
            recorder.record(
                IndexingResult.LogResult(50, emptyList(), Status.SYNCING),
                10.milliseconds,
            )
            // Resumes forward progress
            recorder.record(
                IndexingResult.LogResult(110, emptyList(), Status.SYNCING),
                10.milliseconds,
            )

            val timer =
                registry.find("processor_duration").tag("indexer_name", INDEXER_NAME).timer()!!

            // First call: 1, skipped, third call: 10 (110 - 100)
            assertEquals(11L, timer.count())
        }
    }

    companion object {
        private const val INDEXER_NAME = "TestIndexer"
    }
}
