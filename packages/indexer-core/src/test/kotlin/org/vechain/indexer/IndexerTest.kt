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
import strikt.assertions.isGreaterThan

@ExtendWith(MockKExtension::class)
internal class IndexerTests {

    @MockK
    private lateinit var responseMocker: IndexerResponseMocker

    @MockK
    private lateinit var thorClient: ThorClient

    private lateinit var indexer: Indexer

    private val getBlockNumberSlot = slot<Long>()
    private val processBlockNumberSlot = slot<Block>()

    private val scope = CoroutineScope(Dispatchers.Default)

    @BeforeEach
    fun setup() {
        every { responseMocker.purgeRecords(any()) } just Runs

        indexer = TestIndexer(responseMocker, thorClient)
    }

    @Test
    fun `Test that SYNCING mode processes blocks`() = runBlocking {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers {
            getTestBlock(
                getBlockNumberSlot.captured
            )
        }
        every { responseMocker.getLastSyncedBlockNumber() } returns 0
        every { responseMocker.processBlock(any()) } just Runs

        val job = launch {
            indexer.start()
        }

        delay(1000L)
        job.cancelAndJoin()

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isGreaterThan(100L)
        expectThat(indexer.status).isEqualTo(Status.SYNCING)
        verify(exactly = 1) { responseMocker.purgeRecords(0) }
        verify(atLeast = indexer.currentBlockNumber.toInt()) { responseMocker.processBlock(any()) }
    }

    @Test
    fun `Test that indexer switches to FULLY_SYNCED mode`() {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers {
            if (getBlockNumberSlot.captured >= 100L) {
                throw BlockNotFoundException("Block not found")
            }
            getTestBlock(getBlockNumberSlot.captured)
        }
        every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen 99
        every { responseMocker.processBlock(any()) } just Runs

        scope.launch {
            val job = launch {
                indexer.start()
            }

            // Run the indexer for 1 second
            delay(1000L)

            job.cancel() // Cancel the job

            // Optionally, wait for the job to complete gracefully
            job.join()
        }

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(100L)
        expectThat(indexer.status).isEqualTo(Status.FULLY_SYNCED)
        verify(exactly = 1) { responseMocker.purgeRecords(0) }
        verify(atLeast = indexer.currentBlockNumber.toInt()) { responseMocker.processBlock(any()) }
    }

    @Test
    fun `Test that a reorg triggers a purge`() {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers {
            // At block 100, the parent id is invalid
            val parentId = if (getBlockNumberSlot.captured == 100L)
                "0x02321321"
            else "0x${getBlockNumberSlot.captured - 1}"
            getTestBlock(getBlockNumberSlot.captured, parentId)
        }

        every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen 99
        every { responseMocker.processBlock(any()) } just Runs

        scope.launch {
            val job = launch {
                indexer.start()
            }

            // Run the indexer for 1 second
            delay(1000L)

            job.cancel() // Cancel the job

            // Optionally, wait for the job to complete gracefully
            job.join()
        }

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(99L)
        expectThat(indexer.status).isEqualTo(Status.SYNCING)
        verify(exactly = 1) { responseMocker.purgeRecords(0) }
        // The reorg at block 100 should trigger a purge of block 99 data
        verify(exactly = 1) { responseMocker.purgeRecords(99) }
    }

    @Test
    fun `Test that an unknown exception triggers a purge`() {
        coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers {
            getTestBlock(
                getBlockNumberSlot.captured
            )
        }
        every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen 99
        every { responseMocker.processBlock(capture(processBlockNumberSlot)) } answers {
            if (processBlockNumberSlot.captured.number == 100L) {
                throw Exception("Unknown exception")
            }
        }

        scope.launch {
            val job = launch {
                indexer.start()
            }

            // Run the indexer for 1 second
            delay(1000L)

            job.cancel() // Cancel the job

            // Optionally, wait for the job to complete gracefully
            job.join()
        }

        // Add assertions or verify other expected behavior
        expectThat(indexer.currentBlockNumber).isEqualTo(99L)
        expectThat(indexer.status).isEqualTo(Status.SYNCING)
        verify(exactly = 1) { responseMocker.purgeRecords(0) }
        // The exception thrown at block 100 should trigger a purge of block 99 data
        verify(exactly = 1) { responseMocker.purgeRecords(99) }
    }


}

