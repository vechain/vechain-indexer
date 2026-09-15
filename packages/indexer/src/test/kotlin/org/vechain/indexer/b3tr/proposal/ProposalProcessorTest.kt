package org.vechain.indexer.b3tr.proposal

import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.IndexingResult
import org.vechain.indexer.Status
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.config.CheckpointProperties
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.postgres.IndexerStateRepository
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded
import org.vechain.indexer.utils.BlockDetails

/** The status refresh runs at the chain head only, and one entry writes its rows once. */
@ExtendWith(MockKExtension::class)
internal class ProposalProcessorTest {
    @MockK lateinit var resultService: ProposalResultService

    @MockK lateinit var commentService: ProposalCommentService

    @MockK lateinit var repository: ProposalWriteRepository

    @MockK lateinit var thorClient: ThorClient

    @MockK lateinit var state: IndexerStateRepository

    private lateinit var processor: ProposalProcessor

    private val head = 120L

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { repository.save(any(), any()) } just Runs
        processor =
            ProposalProcessor(
                resultService,
                commentService,
                repository,
                thorClient,
                state,
                CheckpointProperties(),
                InlineVersioningProperties(),
                mockk(relaxed = true),
            )
    }

    private fun blockId(number: Long): String = toHex(number, 64)

    private fun vote(blockNumber: Long): IndexedEvent =
        buildIndexedEvent(
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            eventType = "B3TR_ProposalVote",
        )

    private fun entry(status: Status, events: List<IndexedEvent> = listOf(vote(head))) =
        IndexingResult.LogResult(head, events, status)

    private fun result(proposalId: String, blockNumber: Long, state: ProposalState) =
        ProposalResult(
            proposalId = proposalId,
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            createdAtBlockNumber = 10,
            startRoundId = 7,
            state = state,
            results = null,
            description = "proposal $proposalId",
        )

    private fun comment(proposalId: String, blockNumber: Long) =
        ProposalComment(
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            voter = "0x" + "aa".repeat(20),
            proposalId = proposalId,
            support = Support.FOR,
            weight = BigInteger.valueOf(100),
            power = BigInteger.TEN,
            reason = "well argued",
        )

    private fun headBlock(): BlockUnexpanded =
        mockk<BlockUnexpanded>(relaxed = true).also {
            every { it.id } returns blockId(head)
            every { it.number } returns head
            every { it.timestamp } returns head * 10
        }

    @Test
    fun `the chain head refreshes states and saves them with the rows the entry built`(): Unit =
        runBlocking {
            val tallied = result("1", head, ProposalState.Active)
            val refreshed = result("2", head, ProposalState.Executed)
            val reason = comment("1", head)
            every { resultService.processEvents(any()) } returns listOf(tallied)
            every { commentService.processEvents(any()) } returns listOf(reason)
            coEvery { thorClient.getBlockUnexpanded(BlockRevision.Number(head)) } returns
                headBlock()
            coEvery { resultService.refreshStates(any(), any()) } returns listOf(refreshed)

            processor.processEntry(entry(Status.FULLY_SYNCED))

            // The refresh sees this entry's own rows, and both sets land in one save.
            coVerify {
                resultService.refreshStates(
                    BlockDetails(blockId(head), head, head * 10),
                    listOf(tallied),
                )
            }
            verify { repository.save(listOf(tallied, refreshed), listOf(reason)) }
        }

    @Test
    fun `a syncing entry saves its rows without reading the chain head`(): Unit = runBlocking {
        val tallied = result("1", head, ProposalState.Active)
        every { resultService.processEvents(any()) } returns listOf(tallied)
        every { commentService.processEvents(any()) } returns emptyList()

        processor.processEntry(entry(Status.SYNCING))

        coVerify(exactly = 0) { thorClient.getBlockUnexpanded(any()) }
        coVerify(exactly = 0) { resultService.refreshStates(any(), any()) }
        verify { repository.save(listOf(tallied), emptyList()) }
    }

    @Test
    fun `an entry whose states all held writes nothing`(): Unit = runBlocking {
        every { resultService.processEvents(any()) } returns emptyList()
        every { commentService.processEvents(any()) } returns emptyList()
        coEvery { thorClient.getBlockUnexpanded(BlockRevision.Number(head)) } returns headBlock()
        coEvery { resultService.refreshStates(any(), any()) } returns emptyList()

        processor.processEntry(entry(Status.FULLY_SYNCED, emptyList()))

        verify(exactly = 0) { repository.save(any(), any()) }
    }
}
