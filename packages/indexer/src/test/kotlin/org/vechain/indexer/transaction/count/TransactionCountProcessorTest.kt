package org.vechain.indexer.transaction.count

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.checkpoint.CheckpointService
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.transaction.TransactionCountSummary
import org.vechain.indexer.transaction.TransactionCountSummaryRepository

class TransactionCountProcessorTest {
    @MockK lateinit var repository: TransactionCountSummaryRepository
    @MockK lateinit var mongoTemplate: MongoTemplate
    @MockK lateinit var service: TransactionCountService
    @MockK lateinit var checkpointService: CheckpointService

    private lateinit var processor: TransactionCountProcessor

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        processor =
            TransactionCountProcessor(
                repository = repository,
                mongoTemplate = mongoTemplate,
                service = service,
                checkpointService = checkpointService,
                processorMetrics = mockk(relaxed = true),
            )
        every { checkpointService.trySaveCheckpoint(any(), any()) } just Runs
    }

    @Test
    fun `process skips save when service marks block as no-op`() = runBlocking {
        val block = mockBlock(100)
        every { service.processBlock(block) } returns
            TransactionCountService.ProcessingResult(
                current = mockk(),
                previous = mockk(),
                shouldPersist = false,
            )

        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )

        verify(exactly = 1) { service.processBlock(block) }
        verify(exactly = 0) { service.save(any(), any()) }
        verify(exactly = 1) { checkpointService.trySaveCheckpoint("transaction_counts", 100) }
    }

    @Test
    fun `process saves when service marks block as changed`() = runBlocking {
        val block = mockBlock(100)
        val current = mockk<TransactionCountSummary>()
        val previous = mockk<TransactionCountSummary>()
        every { service.processBlock(block) } returns
            TransactionCountService.ProcessingResult(
                current = current,
                previous = previous,
                shouldPersist = true,
            )
        every { service.save(current, previous) } just Runs

        processor.process(
            IndexingResult.BlockResult(block, emptyList(), emptyList(), Status.SYNCING)
        )

        verify(exactly = 1) { service.processBlock(block) }
        verify(exactly = 1) { service.save(current, previous) }
        verify(exactly = 1) { checkpointService.trySaveCheckpoint("transaction_counts", 100) }
    }

    private fun mockBlock(number: Long): Block = mockk {
        every { this@mockk.number } returns number
    }
}
