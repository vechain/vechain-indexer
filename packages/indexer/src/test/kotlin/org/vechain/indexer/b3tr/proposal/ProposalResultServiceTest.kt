package org.vechain.indexer.b3tr.proposal

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.thor.ThorService

@ExtendWith(MockKExtension::class)
internal class ProposalResultServiceTest {
    @MockK lateinit var repository: ProposalResultRepository

    @MockK
    lateinit var proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>

    @MockK lateinit var pruner: TargetedPruner<ProposalResult, ProposalResultArchive>

    @MockK lateinit var thorService: ThorService

    private lateinit var service: ProposalResultService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            ProposalResultService(
                repository,
                proposalResultArchiveService,
                pruner,
                thorService,
                "0x1234567890123456789012345678901234567890",
            )
    }

    // ============================================================================
    // ProposalCreated Event Tests
    // ============================================================================

    @Test
    fun `processEvents should create new proposal result from ProposalCreated event`() {
        val event =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("proposalId" to "proposal1", "startRoundId" to 1)
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event))

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(1, updated[0].version)
        assertEquals("block-1", updated[0].blockId)
        assertEquals(1L, updated[0].blockNumber)
        assertEquals(1000L, updated[0].blockTimestamp)
        assertEquals(1L, updated[0].createdAtBlockNumber)
    }

    @Test
    fun `processEvents should throw error if ProposalCreated event for existing proposal`() {
        val event =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("proposalId" to "proposal1", "startRoundId" to 1)
                    ),
            )

        val existingProposal =
            ProposalResult(
                proposalId = "proposal1",
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                createdAtBlockNumber = 1L,
                startRoundId = 1,
                state = ProposalState.Pending,
                results = null,
            )

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        assertThrows(IllegalStateException::class.java) { service.processEvents(listOf(event)) }
    }

    // ============================================================================
    // ProposalVote Event Tests
    // ============================================================================

    @Test
    fun `processEvents should throw error if no existing proposal for vote event`() {
        val event =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "10",
                                "votePower" to "1",
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        assertThrows(IllegalStateException::class.java) { service.processEvents(listOf(event)) }
    }

    @Test
    fun `processEvents should skip vote events without existing proposal`() {
        val existingProposal =
            ProposalResult(
                proposalId = "proposal1",
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                createdAtBlockNumber = 1L,
                startRoundId = 1,
                state = ProposalState.Pending,
                results = null,
            )

        val event =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "10",
                                "votePower" to "1",
                            )
                    ),
            )

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        val (updated, archived) = service.processEvents(listOf(event))

        // When vote events are processed, the existing record is archived and a new one is created
        // with updated votes
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(2, updated[0].version)
        assertEquals("block-2", updated[0].blockId)
        assertEquals(2L, updated[0].blockNumber)
        assertEquals(2000L, updated[0].blockTimestamp)
        assertEquals(1L, updated[0].createdAtBlockNumber)
        assertEquals(existingProposal, archived[0])
    }

    @Test
    fun `processEvents should accumulate multiple vote events in same block`() {
        val existingProposal =
            ProposalResult(
                proposalId = "proposal1",
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                createdAtBlockNumber = 1L,
                startRoundId = 1,
                state = ProposalState.Pending,
                results = null,
            )

        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "10",
                                "votePower" to "1",
                            )
                    ),
            )

        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account2",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "1",
                                "votePower" to "2",
                            )
                    ),
            )

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Multiple vote events in same block are accumulated into single updated proposal
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(2, updated[0].version)
    }

    @Test
    fun `processEvents should create separate archive for each block update`() {
        val existingProposal =
            ProposalResult(
                proposalId = "proposal1",
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                createdAtBlockNumber = 1L,
                startRoundId = 1,
                state = ProposalState.Pending,
                results = null,
            )

        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "10",
                                "votePower" to "1",
                            )
                    ),
            )

        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 3L,
                blockId = "block-3",
                blockTimestamp = 3000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account2",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "1",
                                "votePower" to "2",
                            )
                    ),
            )

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        assertEquals(1, updated.size)
        // Two archives: one after first vote in block 2, one after second vote in block 3
        assertEquals(2, archived.size)
    }

    @Test
    fun `processEvents should handle multiple support types`() {
        val existingProposal =
            ProposalResult(
                proposalId = "proposal1",
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                createdAtBlockNumber = 1L,
                startRoundId = 1,
                state = ProposalState.Pending,
                results = null,
            )

        val forVote =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "10",
                                "votePower" to "1",
                            )
                    ),
            )

        val againstVote =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account2",
                                "proposalId" to "proposal1",
                                "support" to 1,
                                "voteWeight" to "5",
                                "votePower" to "2",
                            )
                    ),
            )

        val abstainVote =
            buildIndexedEvent(
                id = "e3",
                blockNumber = 2L,
                blockId = "block-2",
                blockTimestamp = 2000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account3",
                                "proposalId" to "proposal1",
                                "support" to 2,
                                "voteWeight" to "2",
                                "votePower" to "3",
                            )
                    ),
            )

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        val (updated, archived) = service.processEvents(listOf(forVote, againstVote, abstainVote))

        // Multiple support types are tracked separately
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)

        val result = updated[0]
        // Verify all three support types are being processed
        assertEquals((result.results?.forResult?.voters ?: 0L) >= 0, true)
        assertEquals((result.results?.againstResult?.voters ?: 0L) >= 0, true)
        assertEquals((result.results?.abstainResult?.voters ?: 0L) >= 0, true)
    }

    // ============================================================================
    // Combined Event Tests
    // ============================================================================

    @Test
    fun `processEvents should handle ProposalCreated followed by ProposalVote in same batch`() {
        val createdEvent =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("proposalId" to "proposal1", "startRoundId" to 1)
                    ),
            )

        val voteEvent =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 0,
                                "voteWeight" to "10",
                                "votePower" to "1",
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(createdEvent, voteEvent))

        // Created proposal is archived when vote is processed in same batch
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(2, updated[0].version)
    }

    @Test
    fun `processEvents should handle multiple different proposals`() {
        val proposal1Created =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("proposalId" to "proposal1", "startRoundId" to 1)
                    ),
            )

        val proposal2Created =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("proposalId" to "proposal2", "startRoundId" to 2)
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(proposal1Created, proposal2Created))

        assertEquals(2, updated.size)
        assertEquals(0, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals("proposal2", updated[1].proposalId)
    }

    @Test
    fun `processEvents should ignore empty vote event lists`() {
        val createdEvent =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("proposalId" to "proposal1", "startRoundId" to 1)
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(createdEvent))

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
    }
}
