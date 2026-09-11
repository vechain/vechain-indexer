package org.vechain.indexer.blocks

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.thor.model.BlockIdentifier

class BlocksProcessorTest {

    private val service = mockk<BlockTreeService>(relaxed = true)
    private val repository = mockk<BlocksWriteRepository>(relaxed = true)
    private val processor = BlocksProcessor(service, repository, mockk(relaxed = true))

    @Test
    fun `processEntry projects the block and saves it`() = runBlocking {
        val block = BlockFixtures.BLOCK_MULTIPLE_TXS
        val projected = mockk<BlockTree>()
        every { service.processBlock(block, emptyList()) } returns projected

        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )

        verify(exactly = 1) { service.save(projected) }
    }

    @Test
    fun `processEntry rejects a log result`() {
        assertThrows<IllegalArgumentException> {
            runBlocking {
                processor.process(IndexingResult.LogResult(1L, emptyList(), Status.SYNCING))
            }
        }
    }

    @Test
    fun `resume comes from the newest block row`() {
        every { repository.lastSynced() } returns BlockIdentifier(42L, "0x2a")

        assertEquals(BlockIdentifier(42L, "0x2a"), processor.getLastSyncedBlock())
    }

    @Test
    fun `rollback clears the totals cache before deleting forward of the block`() {
        every { service.resetCache() } just Runs

        processor.rollback(500L)

        verifyOrder {
            service.resetCache()
            repository.rollbackFrom(500L)
        }
    }

    @Test
    fun `bootstrap runs the version check`() {
        processor.bootstrap()
        verify(exactly = 1) { service.ensureVersion() }
    }
}
