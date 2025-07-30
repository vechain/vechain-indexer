package org.vechain.indexer

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.thor.model.Block

@ExtendWith(MockKExtension::class)
class BaseProcessorTest {
    @MockK lateinit var repository: BaseIndexedRepository<Document, String>

    private lateinit var processor: TestableBaseProcessor

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)

        processor = TestableBaseProcessor(repository)
    }

    @Test
    fun `rollback - deletes blocks in range`() {
        every { repository.deleteAllByBlockNumberBetween(9, 11) } just Runs

        processor.rollback(10)

        verify { repository.deleteAllByBlockNumberBetween(9, 11) }
    }

    @Test
    fun `getLastSyncedBlock - returns null if no records found`() {
        every { repository.getLatestRecord() } returns null

        val result = processor.getLastSyncedBlock()

        assert(result == null) { "Expected null, but got $result" }
    }

    @Test
    fun `getLastSyncedBlock - returns block identifier from latest record`() {
        val blockId = "block123"
        val blockNumber = 42L

        val lastEvent =
            mockk<Document> {
                every { this@mockk.blockId } returns blockId
                every { this@mockk.blockNumber } returns blockNumber
            }

        // Mock the return value
        every { repository.getLatestRecord() } returns lastEvent

        val result = processor.getLastSyncedBlock()

        assert(result != null) { "Expected non-null BlockIdentifier, but got null" }
        assert(result!!.id == blockId) { "Expected block ID $blockId, but got ${result.id}" }
        assert(result.number == blockNumber) {
            "Expected block number $blockNumber, but got ${result.number}"
        }
    }

    data class Document(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
    ) : IndexedDocument

    class TestableBaseProcessor(repository: BaseIndexedRepository<*, *>) :
        BaseProcessor(repository) {

        override fun process(events: List<IndexedEvent>, block: Block?) {
            // does nothing
        }
    }
}
