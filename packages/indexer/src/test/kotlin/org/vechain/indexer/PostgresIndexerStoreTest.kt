package org.vechain.indexer

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.postgres.PostgresIndexerTables
import org.vechain.indexer.thor.model.BlockIdentifier

class PostgresIndexerStoreTest {

    private val tables = mockk<PostgresIndexerTables>(relaxed = true)
    private val state = mockk<IndexerStateRepository>(relaxed = true)
    private val properties = CheckpointProperties()

    private fun store() = PostgresIndexerStore("test", tables, state, properties)

    @Test
    fun `resume is the recorded checkpoint`() {
        every { state.checkpoint("test") } returns BlockIdentifier(42, null)

        assertEquals(BlockIdentifier(42, null), store().lastSynced())
    }

    @Test
    fun `rollback undoes the tables and then moves the checkpoint below the block`() {
        store().rollbackFrom(500)

        verifyOrder {
            tables.rollbackFrom(500)
            state.saveCheckpoint("test", BlockIdentifier(499, null))
        }
    }

    @Test
    fun `checkpoints are throttled by the save interval and flushed unthrottled`() {
        properties.saveIntervalSeconds = 3600
        val store = store()

        store.onProcessed(BlockIdentifier(1, "0x01"))
        store.onProcessed(BlockIdentifier(2, "0x02"))
        verify(exactly = 1) { state.saveCheckpoint("test", BlockIdentifier(1, "0x01")) }
        verify(exactly = 0) { state.saveCheckpoint("test", BlockIdentifier(2, "0x02")) }

        store.flushCheckpoint()
        verify(exactly = 1) { state.saveCheckpoint("test", BlockIdentifier(2, "0x02")) }
    }

    @Test
    fun `a zero interval checkpoints every entry`() {
        properties.saveIntervalSeconds = 0
        val store = store()

        store.onProcessed(BlockIdentifier(1, null))
        store.onProcessed(BlockIdentifier(2, null))

        verify(exactly = 2) { state.saveCheckpoint("test", any()) }
    }

    @Test
    fun `a failed checkpoint write is logged, not thrown`() {
        every { state.saveCheckpoint(any(), any()) } throws IllegalStateException("down")

        store().onProcessed(BlockIdentifier(1, null))
    }

    @Test
    fun `ensureVersion records a first version without touching data`() {
        every { state.storedVersion("test") } returns null

        store().ensureVersion(3)

        verify { state.recordVersion("test", 3) }
        verify(exactly = 0) { state.resync(any(), any(), any()) }
    }

    @Test
    fun `ensureVersion resyncs when the configured version is higher`() {
        every { state.storedVersion("test") } returns 2
        every { state.resync("test", 3, tables) } just Runs

        store().ensureVersion(3)

        verify { state.resync("test", 3, tables) }
    }

    @Test
    fun `ensureVersion leaves a current or newer schema alone`() {
        every { state.storedVersion("test") } returns 3
        store().ensureVersion(3)
        every { state.storedVersion("test") } returns 4
        store().ensureVersion(3)

        verify(exactly = 0) { state.resync(any(), any(), any()) }
        verify(exactly = 0) { state.recordVersion(any(), any()) }
    }

    @Test
    fun `a store without a checkpoint never writes one`() {
        val store =
            object : PostgresIndexerStore("test", tables, state, properties) {
                override val usesCheckpoint = false
            }

        store.onProcessed(BlockIdentifier(1, null))
        store.rollbackFrom(1)
        store.flushCheckpoint()

        verify(exactly = 0) { state.saveCheckpoint(any(), any()) }
        verify { tables.rollbackFrom(1) }
    }
}
