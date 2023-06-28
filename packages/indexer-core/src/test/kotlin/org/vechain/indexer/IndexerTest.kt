package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.TestDataHelper.Companion.getTestBlock
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.thor.model.Block
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class IndexerTests {

    @MockK private lateinit var responseMocker: IndexerResponseMocker

    @MockK private lateinit var thorClient: ThorClient

    private lateinit var indexer: Indexer

    private val getBlockNumberSlot = slot<Long>()
    private val processBlockNumberSlot = slot<Block>()

    @BeforeEach
    fun setup() {
        every { responseMocker.rollback(any()) } just Runs

        indexer = TestIndexer(responseMocker, thorClient)
    }

    @Test
    fun `Test that SYNCING mode processes blocks`() = runBlocking {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
          {
              getTestBlock(getBlockNumberSlot.captured)
          }
        every { responseMocker.getLastSyncedBlockNumber() } returns 0
        every { responseMocker.processBlock(any()) } just Runs

        // Start the indexer in a separate coroutine
        val job = launch { indexer.start(100L) }

        job.join()

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(100L)
        expectThat(indexer.status).isEqualTo(Status.SYNCING)
        verify(exactly = 1) { responseMocker.rollback(0) }
        verify(atLeast = indexer.currentBlockNumber.toInt()) { responseMocker.processBlock(any()) }
    }

    @Test
    fun `Test that indexer switches to FULLY_SYNCED mode`() = runBlocking {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
          {
              if (getBlockNumberSlot.captured >= 99L) {
                  throw BlockNotFoundException("Block not found")
              }
              getTestBlock(getBlockNumberSlot.captured)
          }
        coEvery { thorClient.getBestBlock() } coAnswers { getTestBlock(99L) }
        every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen 99
        every { responseMocker.processBlock(any()) } just Runs

        // Start the indexer in a separate coroutine
        val job = launch { indexer.start(100L) }

        job.join()

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(99L)
        expectThat(indexer.status).isEqualTo(Status.FULLY_SYNCED)
        verify(exactly = 1) { responseMocker.rollback(0) }
        verify(atLeast = indexer.currentBlockNumber.toInt()) { responseMocker.processBlock(any()) }
    }

    @Test
    fun `Test that a reorg triggers a rollback`() = runBlocking {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
          {
              // At block 100, the parent id is invalid
              val parentId =
                if (getBlockNumberSlot.captured == 100L) "0x02321321"
                else "0x${maxOf(getBlockNumberSlot.captured - 1, 0)}"
              getTestBlock(getBlockNumberSlot.captured, parentId)
          }

        every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen 99
        every { responseMocker.processBlock(any()) } just Runs

        // Start the indexer in a separate coroutine
        val job = launch { indexer.start(101L) }

        job.join()

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(99L)
        expectThat(indexer.status).isEqualTo(Status.SYNCING)
        verify(exactly = 1) { responseMocker.rollback(0L) }
        // The reorg at block 100 should trigger a rollback of block 99 data
        verify(exactly = 1) { responseMocker.rollback(99L) }
    }

    @Test
    fun `Test that an unknown exception triggers a rollback`() = runBlocking {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
          {
              getTestBlock(getBlockNumberSlot.captured)
          }
        every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen 99
        every { responseMocker.processBlock(capture(processBlockNumberSlot)) } answers
          {
              if (processBlockNumberSlot.captured.number == 100L) {
                  throw Exception("Unknown exception")
              }
          }

        // Start the indexer in a separate coroutine
        val job = launch { indexer.start(101L) }

        job.join()

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(99L)
        expectThat(indexer.status).isEqualTo(Status.SYNCING)
        verify(exactly = 1) { responseMocker.rollback(0L) }
        // The exception thrown at block 100 should trigger a rollback of block 99 data
        verify(exactly = 1) { responseMocker.rollback(99L) }
    }
}
