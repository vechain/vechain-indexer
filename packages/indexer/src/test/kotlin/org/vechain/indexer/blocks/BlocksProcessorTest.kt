package org.vechain.indexer.blocks

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.blocks.repository.BlockRepository
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.config.metrics.ProcessorMetrics
import org.vechain.indexer.thor.model.Block

class BlocksProcessorTest {

    private val repository = mockk<BlockRepository>(relaxed = true)
    private val service = mockk<BlocksService>(relaxed = true)
    private val checkpointService = mockk<CheckpointService>(relaxed = true)
    private val processorMetrics = mockk<ProcessorMetrics>(relaxed = true)

    private val processor =
        BlocksProcessor(repository, service, checkpointService, processorMetrics)

    private fun block(number: Long) =
        Block(
            number = number,
            id = "0xblock-$number",
            size = 361,
            parentID = "0xblock-${number - 1}",
            timestamp = 1_700_000_000,
            gasLimit = 40_000_000,
            beneficiary = "0xbeneficiary",
            gasUsed = 0,
            totalScore = 1,
            txsRoot = "0xtxsRoot",
            txsFeatures = 1,
            stateRoot = "0xstateRoot",
            receiptsRoot = "0xreceiptsRoot",
            com = true,
            signer = "0xsigner",
            isTrunk = true,
            isFinalized = false,
        )

    @Test
    fun `rollback deletes from the reorg block forward and rewinds the checkpoint`() {
        processor.rollback(500L)

        verify(exactly = 1) { repository.deleteAllByBlockNumberGreaterThanEqual(500L) }
        verify(exactly = 1) { checkpointService.saveCheckpoint("blocks", 499L) }
    }

    @Test
    fun `processEntry projects and saves the block`() = runBlocking {
        val source = block(100L)
        val projected = BlocksService(mockk(relaxed = true)).processBlock(source)
        every { service.processBlock(source) } returns projected

        processor.processEntry(
            IndexingResult.BlockResult(source, emptyList(), emptyList(), Status.SYNCING)
        )

        verify(exactly = 1) { service.save(projected) }
    }

    @Test
    fun `processEntry rejects a log result`() {
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                processor.processEntry(IndexingResult.LogResult(1L, emptyList(), Status.SYNCING))
            }
        }
    }
}
