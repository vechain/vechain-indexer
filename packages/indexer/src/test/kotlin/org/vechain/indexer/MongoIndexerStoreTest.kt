package org.vechain.indexer

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.model.BlockIdentifier

class MongoIndexerStoreTest {

    private val repository = mockk<BaseIndexedRepository<Row, String>>()
    private val checkpointService = mockk<CheckpointService>()
    private val store = MongoIndexerStore(repository, checkpointService, "rows", "RowsIndexer")

    data class Row(
        override val blockId: String,
        override val blockNumber: Long,
        override val blockTimestamp: Long,
    ) : IndexedDocument

    @Test
    fun `lastSynced is null when neither a row nor a checkpoint exists`() {
        every { checkpointService.getCheckpoint("rows") } returns null
        every { repository.getLatestRecord() } returns null

        assertNull(store.lastSynced())
    }

    @Test
    fun `lastSynced takes whichever of row and checkpoint is further ahead`() {
        every { checkpointService.getCheckpoint("rows") } returns BlockIdentifier(100L, null)
        every { repository.getLatestRecord() } returns Row("0xabc", 42L, 0L)
        assertEquals(100L, store.lastSynced()?.number)

        every { repository.getLatestRecord() } returns Row("0xabc", 200L, 0L)
        assertEquals(BlockIdentifier(200L, "0xabc"), store.lastSynced())
    }

    @Test
    fun `lastSynced propagates a failing repository read`() {
        every { checkpointService.getCheckpoint("rows") } returns null
        every { repository.getLatestRecord() } throws IllegalStateException("down")

        assertThrows<IllegalStateException> { store.lastSynced() }
    }

    @Test
    fun `rollbackFrom rewinds the checkpoint before deleting forward of the block`() {
        every { checkpointService.saveCheckpoint("rows", 9L) } just Runs
        every { repository.deleteAllByBlockNumberGreaterThanEqual(10L) } just Runs

        store.rollbackFrom(10L)

        verifyOrder {
            checkpointService.saveCheckpoint("rows", 9L)
            repository.deleteAllByBlockNumberGreaterThanEqual(10L)
        }
    }
}
