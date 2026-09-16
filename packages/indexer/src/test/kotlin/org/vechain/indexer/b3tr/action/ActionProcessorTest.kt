package org.vechain.indexer.b3tr.action

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.b3tr.shared.EntityType
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.postgres.IndexerStateRepository

class ActionProcessorTest {
    private val service: ActionSummaryService = mockk()
    private val repository: ActionWriteRepository = mockk()
    private val processor =
        ActionProcessor(
            service,
            repository,
            mockk<IndexerStateRepository>(relaxed = true),
            CheckpointProperties(),
            InlineVersioningProperties(),
            mockk(relaxed = true),
        )

    private fun entry(block: Long) =
        IndexingResult.LogResult(
            block,
            listOf(
                buildIndexedEvent(
                    blockNumber = block,
                    eventType = "B3TR_ActionReward",
                    params = AbiEventParameters(returnValues = mapOf("cycle" to "4")),
                )
            ),
            Status.SYNCING,
        )

    private fun result(round: Int) =
        ActionSummaryService.Result(
            ActionSummaryUpdate(
                entities =
                    listOf(
                        EntityActionSummary(
                            EntityType.GLOBAL,
                            "GLOBAL",
                            ActionPeriod.AllTime,
                            "0x01",
                            1,
                            1,
                            1,
                            BigDecimal.ONE,
                            null,
                        )
                    )
            ),
            round,
        )

    @Test
    fun `a failed save leaves the entry to be replayed from the round it started in`() {
        coEvery { service.roundBefore(1) } returns 3
        every { service.processEvents(any(), 3) } returns result(4)
        every { service.processEvents(any(), 4) } returns result(4)
        every { service.save(any()) } throws IllegalStateException("connection lost") andThen Unit

        assertThrows(IllegalStateException::class.java) {
            runBlocking { processor.process(entry(1)) }
        }
        runBlocking { processor.process(entry(1)) }
        runBlocking { processor.process(entry(2)) }

        // Both attempts at block 1 start from round 3; only the saved one carries round 4 forward.
        verify(exactly = 2) { service.processEvents(any(), 3) }
        verify(exactly = 1) { service.processEvents(any(), 4) }
        coVerify(exactly = 2) { service.roundBefore(1) }
    }

    @Test
    fun `a rollback forgets the round, so the next entry asks the contract again`() {
        coEvery { service.roundBefore(any()) } returns 3
        every { service.processEvents(any(), 3) } returns result(3)
        every { service.save(any()) } just Runs
        every { service.forget() } just Runs
        every { repository.rollbackFrom(any()) } just Runs

        runBlocking { processor.process(entry(1)) }
        processor.rollback(1)
        runBlocking { processor.process(entry(1)) }

        coVerify(exactly = 2) { service.roundBefore(1) }
    }
}
