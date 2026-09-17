package org.vechain.indexer

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.thor.model.BlockIdentifier

/** indexer-core retries a failed entry without rolling back, so the processor undoes it first. */
class BaseProcessorRetryTest {

    private val store = RecordingStore()
    private val processor = FlakyProcessor(store)

    @Test
    fun `a retried entry is rolled back to its first block before it runs again`() = runBlocking {
        processor.failNext = true
        assertTrue(runCatching { processor.process(logEntry(100, 101)) }.isFailure)
        assertEquals(emptyList<Long>(), store.rolledBack)

        processor.process(logEntry(100, 103))
        assertEquals(listOf(100L), store.rolledBack)
        assertEquals(listOf(103L), processor.processed)

        processor.process(logEntry(104, 105))
        assertEquals(listOf(100L), store.rolledBack)
    }

    @Test
    fun `a rollback between the failure and the retry stands in for the replay`() = runBlocking {
        processor.failNext = true
        assertTrue(runCatching { processor.process(blockEntry(100)) }.isFailure)

        processor.rollback(90)
        processor.process(blockEntry(90))

        assertEquals(listOf(90L), store.rolledBack)
    }

    private fun blockEntry(number: Long) =
        IndexingResult.BlockResult(
            BlockFixtures.BLOCK_NO_CLAUSES.copy(number = number),
            emptyList(),
            emptyList(),
            Status.SYNCING,
        )

    private fun logEntry(first: Long, last: Long) =
        IndexingResult.LogResult(last, (first..last).map(::event), Status.FAST_SYNCING)

    private fun event(block: Long) =
        IndexedEvent(
            id = "event-$block",
            blockId = "0x" + block.toString(16).padStart(64, '0'),
            blockNumber = block,
            blockTimestamp = block * 10,
            txId = "0x" + "1".repeat(64),
            origin = null,
            params = AbiEventParameters(returnValues = emptyMap()),
            eventType = "Transfer",
            clauseIndex = 0,
        )

    private class RecordingStore : IndexerStore {
        val rolledBack = mutableListOf<Long>()

        override fun lastSynced(): BlockIdentifier? = null

        override fun rollbackFrom(blockNumber: Long) {
            rolledBack += blockNumber
        }
    }

    private class FlakyProcessor(store: IndexerStore) :
        BaseProcessor(store, "flaky", ProcessorMetrics(SimpleMeterRegistry())) {
        var failNext = false
        val processed = mutableListOf<Long>()

        override suspend fun processEntry(entry: IndexingResult) {
            if (failNext) {
                failNext = false
                error("the connection went away")
            }
            processed += entry.latestBlockNumber()
        }
    }
}
