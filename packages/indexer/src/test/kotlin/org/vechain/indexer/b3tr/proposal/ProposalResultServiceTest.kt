package org.vechain.indexer.b3tr.proposal

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class ProposalResultServiceTest {
    @MockK lateinit var repository: ProposalWriteRepository

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: TestableProposalResultService

    private val governor = "0x1234567890123456789012345678901234567890"

    /** Opens the two protected helpers that shape the status read. */
    private inner class TestableProposalResultService(
        repository: ProposalWriteRepository,
        thorClient: ThorClient,
    ) : ProposalResultService(repository, thorClient, governor) {
        fun statusClauses(proposals: List<ProposalResult>) = createStatusClauses(proposals)

        fun proposalState(response: InspectionResult, proposalId: String) =
            parseProposalState(response, proposalId)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { repository.findCurrent(any()) } returns emptyList()
        every { repository.findCurrentByStates(any()) } returns emptyList()
        service = TestableProposalResultService(repository, thorClient)
    }

    private fun blockId(number: Long): String = toHex(number, 64)

    private fun block(number: Long) = BlockDetails(blockId(number), number, number * 10)

    private fun created(proposalId: String, blockNumber: Long): IndexedEvent =
        buildIndexedEvent(
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            eventType = "B3TR_ProposalCreated",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "proposalId" to proposalId,
                            "startRoundId" to 7,
                            "description" to "a proposal",
                        )
                ),
        )

    private fun vote(
        proposalId: String,
        blockNumber: Long,
        support: Support = Support.FOR,
        weight: String = "10",
        power: String = "3",
    ): IndexedEvent =
        buildIndexedEvent(
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            eventType = "B3TR_ProposalVote",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "from" to "0x" + "aa".repeat(20),
                            "proposalId" to proposalId,
                            "support" to support.value,
                            "voteWeight" to weight,
                            "votePower" to power,
                        )
                ),
        )

    private fun result(
        proposalId: String = "1",
        blockNumber: Long = 1L,
        state: ProposalState = ProposalState.Pending,
        results: VoteResults? = null,
    ) =
        ProposalResult(
            proposalId = proposalId,
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            createdAtBlockNumber = 1L,
            startRoundId = 7,
            state = state,
            results = results,
            description = "a proposal",
        )

    private fun stateResponse(state: ProposalState) =
        InspectionResult(
            vmError = null,
            data = "0x" + state.ordinal.toString(16).padStart(64, '0'),
            reverted = false,
            events = emptyList(),
            transfers = emptyList(),
            gasUsed = 0,
        )

    @Test
    fun `a creation starts a proposal and its votes accumulate block by block`() {
        val rows = service.processEvents(listOf(created("1", 1), vote("1", 1), vote("1", 2)))

        assertEquals(listOf(1L, 2L), rows.map { it.blockNumber })
        val opened = rows.first()
        assertEquals(ProposalState.Pending, opened.state)
        assertEquals(1L, opened.createdAtBlockNumber)
        assertEquals(7, opened.startRoundId)
        assertEquals(1L, opened.results?.forResult?.voters)

        val second = rows.last()
        assertEquals(2L, second.results?.forResult?.voters)
        assertEquals(BigInteger.valueOf(20), second.results?.forResult?.totalWeight)
        assertEquals(BigInteger.valueOf(6), second.results?.forResult?.totalPower)
        assertEquals(0L, second.results?.againstResult?.voters)
    }

    @Test
    fun `a vote adds to the row the schema already holds`() {
        every { repository.findCurrent(setOf("1")) } returns
            listOf(
                result(
                    blockNumber = 5,
                    results =
                        VoteResults(
                            forResult = Result(2, BigInteger.TEN, BigInteger.ONE),
                            againstResult = Result(0, BigInteger.ZERO, BigInteger.ZERO),
                            abstainResult = Result(0, BigInteger.ZERO, BigInteger.ZERO),
                        ),
                )
            )

        val rows = service.processEvents(listOf(vote("1", 9, Support.AGAINST, "4", "2")))

        assertEquals(1, rows.size)
        assertEquals(9L, rows[0].blockNumber)
        assertEquals(2L, rows[0].results?.forResult?.voters)
        assertEquals(BigInteger.valueOf(4), rows[0].results?.againstResult?.totalWeight)
    }

    @Test
    fun `a vote for an unknown proposal and a second creation both fail`() {
        assertThrows(IllegalStateException::class.java) {
            service.processEvents(listOf(vote("1", 1)))
        }

        every { repository.findCurrent(setOf("1")) } returns listOf(result())
        assertThrows(IllegalArgumentException::class.java) {
            service.processEvents(listOf(created("1", 2)))
        }
    }

    @Test
    fun `only a proposal whose state moved is written again`(): Unit = runBlocking {
        every { repository.findCurrentByStates(any()) } returns
            listOf(result(proposalId = "1"), result(proposalId = "2"))
        coEvery { thorClient.inspectClauses(any(), any()) } returns
            listOf(stateResponse(ProposalState.Active), stateResponse(ProposalState.Pending))

        val refreshed = service.refreshStates(block(30), emptyList())

        assertEquals(1, refreshed.size)
        assertEquals("1", refreshed[0].proposalId)
        assertEquals(ProposalState.Active, refreshed[0].state)
        assertEquals(30L, refreshed[0].blockNumber)
    }

    @Test
    fun `the refresh reads the row this entry just built, not the stale one`(): Unit = runBlocking {
        val tallied =
            result(
                blockNumber = 30,
                results =
                    VoteResults(
                        forResult = Result(1, BigInteger.TEN, BigInteger.ONE),
                        againstResult = Result(0, BigInteger.ZERO, BigInteger.ZERO),
                        abstainResult = Result(0, BigInteger.ZERO, BigInteger.ZERO),
                    ),
            )
        every { repository.findCurrentByStates(any()) } returns listOf(result(blockNumber = 5))
        coEvery { thorClient.inspectClauses(any(), any()) } returns
            listOf(stateResponse(ProposalState.Active))

        val refreshed = service.refreshStates(block(30), listOf(tallied))

        assertEquals(1, refreshed.size)
        assertEquals(1L, refreshed[0].results?.forResult?.voters)
        assertEquals(ProposalState.Active, refreshed[0].state)
    }

    @Test
    fun `a status clause is built per proposal and the ordinal decodes to its state`() {
        assertEquals(1, service.statusClauses(listOf(result())).size)
        assertEquals(governor, service.statusClauses(listOf(result()))[0].to)
        assertEquals(
            ProposalState.Executed,
            service.proposalState(stateResponse(ProposalState.Executed), "1"),
        )
    }
}
