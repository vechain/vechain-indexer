package org.vechain.indexer

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.BlockTestBuilder.Companion.buildBlock
import org.vechain.indexer.exception.BlockNotFoundException
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Block
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class IndexerTest {

    @MockK private lateinit var responseMocker: IndexerResponseMocker

    @MockK private lateinit var thorClient: ThorClient

    private lateinit var indexer: Indexer

    private val getBlockNumberSlot = slot<Long>()
    private val processBlockNumberSlot = slot<Block>()

    @BeforeEach
    fun setup() {
        every { responseMocker.rollback(any()) } just Runs

        indexer = IndexerMock(responseMocker, thorClient)
    }

    @Nested
    inner class IndexerStart {
        @Test
        fun `Start indexer should initialise with rolling back last synced block`() = runBlocking {
            val lastSyncedBlockNumber = 0L
            val indexerIterationsNumber = 1L

            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    buildBlock(getBlockNumberSlot.captured)
                }
            every { responseMocker.getLastSyncedBlockNumber() } returns lastSyncedBlockNumber
            every { responseMocker.processBlock(any()) } just Runs

            val job = launch { indexer.start(indexerIterationsNumber) }
            job.join()

            expect {
                // Verify the status is SYNCING
                that(indexer.status).isEqualTo(Status.SYNCING)
                // Verify the rollback is performed once
                verify(exactly = 1) { responseMocker.rollback(lastSyncedBlockNumber) }
            }
        }

        @Test
        fun `Start indexer should process blocks`() = runBlocking {
            val lastSyncedBlockNumber = 0L
            val indexerIterationsNumber = 100L

            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    buildBlock(getBlockNumberSlot.captured)
                }
            every { responseMocker.getLastSyncedBlockNumber() } returns lastSyncedBlockNumber
            every { responseMocker.processBlock(any()) } just Runs

            val job = launch { indexer.start(indexerIterationsNumber) }
            job.join()

            expect {
                // Verify the status is SYNCING
                that(indexer.status).isEqualTo(Status.SYNCING)
                // Verify the correct number of processing of blocks
                verify(atLeast = indexer.currentBlockNumber.toInt()) {
                    responseMocker.processBlock(any())
                }
            }
        }

        @Test
        fun `Start indexer should perform post process blocks`() = runBlocking {
            val lastSyncedBlockNumber = 0L
            val indexerIterationsNumber = 100L

            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    buildBlock(getBlockNumberSlot.captured)
                }
            every { responseMocker.getLastSyncedBlockNumber() } returns lastSyncedBlockNumber
            every { responseMocker.processBlock(any()) } just Runs

            val job = launch { indexer.start(indexerIterationsNumber) }
            job.join()

            expect {
                // Verify the status is SYNCING
                that(indexer.status).isEqualTo(Status.SYNCING)
                // Verify post-processing increments the current block number
                that(indexer.currentBlockNumber).isEqualTo(indexerIterationsNumber)
            }
        }
    }

    @Nested
    inner class IndexerRestart {

        @Test
        fun `Indexer should restart at current block when unknown exception is thrown`() =
            runBlocking {
                val startBlock = 0L
                val iterationsWithoutError = 99L
                val errorBlockNumber = 100L

                coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                    {
                        buildBlock(getBlockNumberSlot.captured)
                    }
                every { responseMocker.getLastSyncedBlockNumber() } returns
                    startBlock andThen
                    iterationsWithoutError
                var calledAlready = false
                every { responseMocker.processBlock(capture(processBlockNumberSlot)) } answers
                    {
                        if (
                            !calledAlready &&
                                processBlockNumberSlot.captured.number == errorBlockNumber
                        ) {
                            calledAlready = true
                            throw Exception("Unknown exception")
                        }
                    }

                // Run the indexer for another two iterations after the error block
                val job = launch { indexer.start(errorBlockNumber + 2) }
                job.join()

                expect {
                    // Indexer should have advanced processing after successfully restarting
                    // processing of faulty block
                    that(indexer.currentBlockNumber).isEqualTo(errorBlockNumber + 1)
                    // Indexer should switch back to SYNCING status error detection
                    that(indexer.status).isEqualTo(Status.SYNCING)
                    // Indexer should restart & rollback processing at the error block number
                    verify(exactly = 1) { responseMocker.rollback(errorBlockNumber) }
                }
            }

        @Test
        fun `Indexer should restart at block previous to current block when a re-organization is detected`() =
            runBlocking {
                val startBlock = 0L
                val iterationsWithoutError = 99L
                val reorgBlockNumber = 100L

                coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                    {
                        // At block 100, the parent id is invalid
                        val parentId =
                            if (getBlockNumberSlot.captured == reorgBlockNumber) "0x02321321"
                            else "0x${maxOf(getBlockNumberSlot.captured - 1, 0)}"
                        buildBlock(getBlockNumberSlot.captured, parentId)
                    }

                every { responseMocker.getLastSyncedBlockNumber() } returns
                    startBlock andThen
                    iterationsWithoutError
                every { responseMocker.processBlock(any()) } just Runs

                // Run indexer for a few iterations more after re-organization detected
                val job = launch { indexer.start(reorgBlockNumber + 2) }
                job.join()

                expect {
                    // Indexer should have advanced processing after successfully restarting
                    // processing of faulty block
                    that(indexer.currentBlockNumber).isEqualTo(reorgBlockNumber)
                    // Indexer should switch back to SYNCING status after re-organization detection
                    expectThat(indexer.status).isEqualTo(Status.SYNCING)
                    // The re-organization at block reorgBlockNumber should trigger a rollback of
                    // block
                    // (reorgBlockNumber - 1) data
                    verify(exactly = 1) { responseMocker.rollback(reorgBlockNumber - 1) }
                }
            }
    }

    @Nested
    inner class IndexerStatus {
        @Test
        fun `Indexer starting & processing block is at the SYNCING status`() = runBlocking {
            val startBlock = 0L
            val iterations = 100L

            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    buildBlock(getBlockNumberSlot.captured)
                }
            every { responseMocker.getLastSyncedBlockNumber() } returns startBlock
            every { responseMocker.processBlock(any()) } just Runs

            val job = launch { indexer.start(iterations) }
            job.join()

            expect {
                // Current block should correspond to number of iterations of indexer run
                that(indexer.currentBlockNumber).isEqualTo(iterations)
                // Status should be SYNCING
                that(indexer.status).isEqualTo(Status.SYNCING)
                // First initialise should roll back to start block
                verify(exactly = 1) { responseMocker.rollback(startBlock) }
                // Number of processed blocks should correspond to current block number
                verify(exactly = indexer.currentBlockNumber.toInt()) {
                    responseMocker.processBlock(any())
                }
            }
        }

        @Test
        fun `Indexer should switch to FULLY_SYNCED status when block not found`() = runBlocking {
            val blockNotFound = 99L

            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    if (getBlockNumberSlot.captured >= blockNotFound) {
                        throw BlockNotFoundException("Block not found")
                    }
                    buildBlock(getBlockNumberSlot.captured)
                }
            coEvery { thorClient.getBestBlock() } coAnswers { buildBlock(blockNotFound) }
            every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen blockNotFound
            every { responseMocker.processBlock(any()) } just Runs

            val job = launch { indexer.start(blockNotFound + 1) }
            job.join()

            expect {
                // The current block remains at the one not found
                that(indexer.currentBlockNumber).isEqualTo(blockNotFound)
                // Status should switch to FULLY_SYNCED
                that(indexer.status).isEqualTo(Status.FULLY_SYNCED)
            }
        }

        @Test
        fun `Indexer should ensure whether it is FULLY_SYNCED and switch back to SYNCING`() =
            runBlocking {
                val iterations = 101L
                val blockNotFound = 99L

                // Block is not found the first time indexer tries to fetch it
                var calledAlready = false
                coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                    {
                        if (!calledAlready && getBlockNumberSlot.captured == blockNotFound) {
                            calledAlready = true
                            throw BlockNotFoundException("Block not found")
                        }
                        buildBlock(getBlockNumberSlot.captured)
                    }
                // Simulate a gap between last synced and current best block from chain
                coEvery { thorClient.getBestBlock() } coAnswers { buildBlock(iterations) }
                every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen blockNotFound
                every { responseMocker.processBlock(any()) } just Runs

                // Iterations + (1 iteration) where block is not found to trigger the FULLY_SYNCED
                // status
                val job = launch { indexer.start(iterations + 1) }
                job.join()

                expect {
                    // Current block number should match the number of iterations after we run
                    // indexer (iterations + 1) number of times
                    that(indexer.currentBlockNumber).isEqualTo(iterations)
                    // Status should switch back to syncing after we detect indexer is behind best
                    // on-chain block
                    that(indexer.status).isEqualTo(Status.SYNCING)
                }
            }

        @Test
        fun `Indexer should switch to REORG status upon chain re-organization`() = runBlocking {
            val reorgBlock = 100L

            // Simulate re-organization by detecting invalid parent block id at reorgBlock
            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    // At reorgBlock, the parent id is invalid
                    val parentId =
                        if (getBlockNumberSlot.captured == reorgBlock) "0x02321321"
                        else "0x${maxOf(getBlockNumberSlot.captured - 1, 0)}"
                    buildBlock(getBlockNumberSlot.captured, parentId)
                }

            every { responseMocker.getLastSyncedBlockNumber() } returns 0 andThen reorgBlock - 1
            every { responseMocker.processBlock(any()) } just Runs

            val job = launch { indexer.start(reorgBlock + 1) }
            job.join()

            expect {
                // The current block number should match the re-organization block
                that(indexer.currentBlockNumber).isEqualTo(reorgBlock)
                // The indexer status should switch to REORG
                that(indexer.status).isEqualTo(Status.REORG)
            }
        }

        @Test
        fun `Indexer should switch to ERROR status upon unknown exception thrown`() = runBlocking {
            val unknownExceptionBlock = 100L

            coEvery { thorClient.getBlock(capture(getBlockNumberSlot)) } coAnswers
                {
                    buildBlock(getBlockNumberSlot.captured)
                }
            every { responseMocker.getLastSyncedBlockNumber() } returns
                0 andThen
                unknownExceptionBlock - 1
            // Exception is thrown when processing block unknownExceptionBlock
            every { responseMocker.processBlock(capture(processBlockNumberSlot)) } answers
                {
                    if (processBlockNumberSlot.captured.number == unknownExceptionBlock) {
                        throw Exception("Unknown exception")
                    }
                }

            val job = launch { indexer.start(unknownExceptionBlock + 1) }
            job.join()

            expect {
                // The current block number should match the exception block
                that(indexer.currentBlockNumber).isEqualTo(unknownExceptionBlock)
                // The indexer status should switch to ERROR
                that(indexer.status).isEqualTo(Status.ERROR)
            }
        }
    }
}
