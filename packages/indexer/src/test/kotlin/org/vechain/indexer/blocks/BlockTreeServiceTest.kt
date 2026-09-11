package org.vechain.indexer.blocks

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.fixtures.BlockFixtures
import org.vechain.indexer.transaction.TransactionService

class BlockTreeServiceTest {

    private val repository = mockk<BlocksWriteRepository>(relaxed = true)
    private val service =
        BlockTreeService(BlocksService(), TransactionService(), repository, version = 3)

    private val genesis = service.processBlock(BlockFixtures.BLOCK_NO_CLAUSES, emptyList())
    private val withTxs = service.processBlock(BlockFixtures.BLOCK_MULTIPLE_TXS, emptyList())

    private fun at(tree: BlockTree, number: Long) =
        tree.copy(
            block = tree.block.copy(blockNumber = number),
            transactions = tree.transactions.map { it.copy(blockNumber = number) },
        )

    @Test
    fun `the first block starts the running totals from zero`() {
        every { repository.newestTotals() } returns null

        service.save(at(withTxs, 0))

        val expected = BlockTotals.ZERO.plus(withTxs.transactions)
        verify {
            repository.insert(
                any(),
                withTxs.transactions.map { it.copy(blockNumber = 0) },
                expected,
            )
        }
    }

    @Test
    fun `a block above genesis needs a stored predecessor`() {
        every { repository.newestTotals() } returns null

        assertThrows<IllegalArgumentException> { service.save(at(withTxs, 5)) }
        verify(exactly = 0) { repository.insert(any(), any(), any()) }
    }

    @Test
    fun `consecutive blocks accumulate from the cached totals without re-reading`() {
        every { repository.newestTotals() } returns BlockTotals(10, 20, 1, 2)

        service.save(at(withTxs, 100))
        service.save(at(genesis, 101))
        service.save(at(withTxs, 102))

        val afterFirst = BlockTotals(10, 20, 1, 2).plus(withTxs.transactions)
        verify(exactly = 1) { repository.newestTotals() }
        verify { repository.insert(any(), any(), afterFirst) }
        verify { repository.insert(match { it.blockNumber == 101L }, emptyList(), afterFirst) }
        verify {
            repository.insert(
                match { it.blockNumber == 102L },
                any(),
                afterFirst.plus(withTxs.transactions),
            )
        }
    }

    @Test
    fun `a gap or a reset cache falls back to the stored totals`() {
        every { repository.newestTotals() } returns BlockTotals(10, 20, 1, 2)
        service.save(at(genesis, 100))
        service.resetCache()

        service.save(at(genesis, 101))
        service.save(at(genesis, 200))

        verify(exactly = 3) { repository.newestTotals() }
    }

    @Test
    fun `ensureVersion records a first version without touching data`() {
        every { repository.storedVersion() } returns null

        service.ensureVersion()

        verify { repository.recordVersion(3) }
        verify(exactly = 0) { repository.resync(any()) }
    }

    @Test
    fun `ensureVersion truncates when the configured version is higher`() {
        every { repository.storedVersion() } returns 2
        every { repository.resync(3) } just Runs

        service.ensureVersion()

        verify { repository.resync(3) }
    }

    @Test
    fun `ensureVersion leaves a current or newer schema alone`() {
        every { repository.storedVersion() } returns 3
        service.ensureVersion()
        every { repository.storedVersion() } returns 4
        service.ensureVersion()

        verify(exactly = 0) { repository.resync(any()) }
        verify(exactly = 0) { repository.recordVersion(any()) }
    }

    @Test
    fun `processBlock projects the block and its transactions`() {
        assertEquals(BlockFixtures.BLOCK_MULTIPLE_TXS.number, withTxs.block.blockNumber)
        assertEquals(BlockFixtures.BLOCK_MULTIPLE_TXS.transactions.size, withTxs.transactions.size)
        assertEquals(withTxs.transactions.map { it.id }, withTxs.block.transactions)
    }
}
