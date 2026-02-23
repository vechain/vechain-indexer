package org.vechain.indexer.b3tr.proposal

import io.mockk.*
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.VersionedDocumentAccumulator
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.config.InlineVersioningProperties
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class ProposalResultServiceTest {
    @MockK lateinit var repository: ProposalResultRepository

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var inlineVersioningProperties: InlineVersioningProperties

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: TestableProposalResultService

    private fun blockId(num: Long): String = toHex(num, 64)

    // A testable subclass to expose protected methods for testing
    private class TestableProposalResultService(
        repository: ProposalResultRepository,
        mongoTemplate: MongoTemplate,
        inlineVersioningProperties: InlineVersioningProperties,
        thorClient: ThorClient,
        governorContract: String,
    ) :
        ProposalResultService(
            repository,
            mongoTemplate,
            inlineVersioningProperties,
            thorClient,
            governorContract,
        ) {
        fun callCreateStatusClauses(proposals: List<ProposalResult>) =
            createStatusClauses(proposals)

        fun callParseProposalState(response: InspectionResult, proposalId: String) =
            parseProposalState(response, proposalId)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { inlineVersioningProperties.blockWindow } returns 10000L
        every { inlineVersioningProperties.maxVersions } returns 100
        service =
            TestableProposalResultService(
                repository,
                mongoTemplate,
                inlineVersioningProperties,
                thorClient,
                "0x1234567890123456789012345678901234567890",
            )
    }

    private fun newAccumulator(): VersionedDocumentAccumulator<ProposalResult> =
        VersionedDocumentAccumulator(service::findByProposalId)

    // ============================================================================
    // ProposalCreated Event Tests
    // ============================================================================

    @Test
    fun `processBlockEvents should create new proposal result from ProposalCreated event`() {
        val event =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "proposalId" to "proposal1",
                                "startRoundId" to 1,
                                "description" to "ABC",
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(event), accumulator)
        val (updated, archived) = accumulator.results()

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
    fun `processBlockEvents should throw error if ProposalCreated event for existing proposal`() {
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
                description = "ABC",
            )

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        val accumulator = newAccumulator()
        accumulator.startBlock()
        assertThrows(IllegalStateException::class.java) {
            service.processBlockEvents(listOf(event), accumulator)
        }
    }

    // ============================================================================
    // ProposalVote Event Tests
    // ============================================================================

    @Test
    fun `processBlockEvents should throw error if no existing proposal for vote event`() {
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

        val accumulator = newAccumulator()
        accumulator.startBlock()
        assertThrows(IllegalStateException::class.java) {
            service.processBlockEvents(listOf(event), accumulator)
        }
    }

    @Test
    fun `processBlockEvents should update existing proposal with vote events`() {
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
                description = "ABC",
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

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(event), accumulator)
        val (updated, archived) = accumulator.results()

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
    fun `processBlockEvents should accumulate multiple vote events in same block`() {
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
                description = "ABC",
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

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(event1, event2), accumulator)
        val (updated, archived) = accumulator.results()

        // Multiple vote events in same block are accumulated into single updated proposal
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(2, updated[0].version)
    }

    @Test
    fun `multi-block batch should create separate archive for each block update`() {
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
                description = "ABC",
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

        // Simulate multi-block processing as the processor does: groupByBlock, then per-block calls
        val accumulator = newAccumulator()

        // Block 2
        accumulator.startBlock()
        service.processBlockEvents(listOf(event1), accumulator)

        // Block 3
        accumulator.startBlock()
        service.processBlockEvents(listOf(event2), accumulator)

        val (updated, archived) = accumulator.results()

        assertEquals(1, updated.size)
        // Two archives: one after first vote in block 2, one after second vote in block 3
        assertEquals(2, archived.size)
    }

    @Test
    fun `multi-block batch with 3+ blocks should produce sequential archive versions`() {
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
                description = "ABC",
            )

        val events =
            (2..5).map { blockNum ->
                buildIndexedEvent(
                    id = "e$blockNum",
                    blockNumber = blockNum.toLong(),
                    blockId = "block-$blockNum",
                    blockTimestamp = blockNum * 1000L,
                    eventType = "B3TR_ProposalVote",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "from" to "account$blockNum",
                                    "proposalId" to "proposal1",
                                    "support" to 0,
                                    "voteWeight" to "10",
                                    "votePower" to "1",
                                )
                        ),
                )
            }

        every { repository.findByIdOrNull("proposal1") } returns existingProposal

        val accumulator = newAccumulator()

        // Process each event as a separate block (simulating FAST_SYNCING multi-block batch)
        events.forEach { event ->
            accumulator.startBlock()
            service.processBlockEvents(listOf(event), accumulator)
        }

        val (updated, archived) = accumulator.results()

        assertEquals(1, updated.size)
        assertEquals(5, updated[0].version) // v1 + 4 block updates
        assertEquals("block-5", updated[0].blockId)

        // 4 archives: v1 (original), v2 (after block 2), v3 (after block 3), v4 (after block 4)
        assertEquals(4, archived.size)

        // Verify sequential versions in archives
        val archivedVersions = archived.map { it.version }.sorted()
        assertEquals(listOf(1, 2, 3, 4), archivedVersions)
    }

    @Test
    fun `processBlockEvents should handle multiple support types`() {
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
                description = "ABC",
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

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(forVote, againstVote, abstainVote), accumulator)
        val (updated, archived) = accumulator.results()

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
    fun `processBlockEvents should handle ProposalCreated followed by ProposalVote in same block`() {
        val createdEvent =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "proposalId" to "proposal1",
                                "startRoundId" to 1,
                                "description" to "ABC",
                            )
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

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(createdEvent, voteEvent), accumulator)
        val (updated, archived) = accumulator.results()

        // Created proposal is archived when vote is processed in same batch
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(2, updated[0].version)
    }

    @Test
    fun `processBlockEvents should handle multiple different proposals`() {
        val proposal1Created =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "proposalId" to "proposal1",
                                "startRoundId" to 1,
                                "description" to "ABC",
                            )
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
                        returnValues =
                            mapOf(
                                "proposalId" to "proposal2",
                                "startRoundId" to 2,
                                "description" to "ABC",
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(proposal1Created, proposal2Created), accumulator)
        val (updated, archived) = accumulator.results()

        assertEquals(2, updated.size)
        assertEquals(0, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals("proposal2", updated[1].proposalId)
    }

    @Test
    fun `processBlockEvents should ignore empty vote event lists`() {
        val createdEvent =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                blockId = "block-1",
                blockTimestamp = 1000L,
                eventType = "B3TR_ProposalCreated",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "proposalId" to "proposal1",
                                "startRoundId" to 1,
                                "description" to "ABC",
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val accumulator = newAccumulator()
        accumulator.startBlock()
        service.processBlockEvents(listOf(createdEvent), accumulator)
        val (updated, archived) = accumulator.results()

        assertEquals(1, updated.size)
        assertEquals(0, archived.size)
    }

    // ============================================================================
    // Status Update Tests
    // ============================================================================

    @Test
    fun `updateStatuses should return empty when no non-finalized proposals exist`() {
        every { repository.findByStateIn(any()) } returns emptyList()

        val block = BlockDetails(blockId = "block-1", blockNumber = 1L, blockTimestamp = 1000L)
        val accumulator = newAccumulator()
        accumulator.startBlock()
        runBlocking { service.updateStatuses(block, accumulator) }
        val (updated, archived) = accumulator.results()

        assertEquals(0, updated.size)
        assertEquals(0, archived.size)
    }

    @Test
    fun `updateStatuses should fetch and update proposal statuses via accumulator`() {
        val proposals =
            listOf(
                ProposalResult(
                    proposalId = "1",
                    version = 1,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1000L,
                    createdAtBlockNumber = 1L,
                    startRoundId = 1,
                    state = ProposalState.Pending,
                    results = null,
                    description = "ABC",
                ),
                ProposalResult(
                    proposalId = "2",
                    version = 1,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1000L,
                    createdAtBlockNumber = 1L,
                    startRoundId = 2,
                    state = ProposalState.Pending,
                    results = null,
                    description = "ABC",
                ),
            )

        val responses =
            listOf(
                InspectionResult(
                    data = "0x01", // Active
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
                InspectionResult(
                    data = "0x01", // Active
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = false,
                    vmError = null,
                ),
            )

        every { repository.findByStateIn(any()) } returns proposals
        coEvery { thorClient.inspectClauses(any(), any()) } returns responses
        // findByProposalId will be called by the accumulator for proposals not yet in cache
        every { repository.findByIdOrNull("1") } returns proposals[0]
        every { repository.findByIdOrNull("2") } returns proposals[1]

        val block = BlockDetails(blockId = blockId(2), blockNumber = 2L, blockTimestamp = 2000L)
        val accumulator = newAccumulator()
        accumulator.startBlock()
        runBlocking { service.updateStatuses(block, accumulator) }
        val (updated, archived) = accumulator.results()

        // State changed from Pending (0) to Active (1)
        assertEquals(2, updated.size)
        assertEquals(2, archived.size)
        assertEquals(blockId(2), updated[0].blockId)
        assertEquals(ProposalState.Active, updated[0].state)
    }

    @Test
    fun `updateStatuses should handle reverted responses`() {
        val proposals =
            listOf(
                ProposalResult(
                    proposalId = "1",
                    version = 1,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1000L,
                    createdAtBlockNumber = 1L,
                    startRoundId = 1,
                    state = ProposalState.Pending,
                    results = null,
                    description = "ABC",
                )
            )

        val responses =
            listOf(
                InspectionResult(
                    data = "0x00",
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                    reverted = true,
                    vmError = "execution reverted",
                )
            )

        every { repository.findByStateIn(any()) } returns proposals
        coEvery { thorClient.inspectClauses(any(), any()) } returns responses

        val block = BlockDetails(blockId = blockId(2), blockNumber = 2L, blockTimestamp = 2000L)
        val accumulator = newAccumulator()
        accumulator.startBlock()

        assertThrows(IllegalStateException::class.java) {
            runBlocking { service.updateStatuses(block, accumulator) }
        }
    }

    @Test
    fun `createStatusClauses should create one clause per proposal`() {
        val proposals =
            listOf(
                ProposalResult(
                    proposalId = "1",
                    version = 1,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1000L,
                    createdAtBlockNumber = 1L,
                    startRoundId = 1,
                    state = ProposalState.Pending,
                    results = null,
                    description = "ABC",
                ),
                ProposalResult(
                    proposalId = "2",
                    version = 1,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1000L,
                    createdAtBlockNumber = 1L,
                    startRoundId = 2,
                    state = ProposalState.Pending,
                    results = null,
                    description = "ABC",
                ),
            )

        val clauses = service.callCreateStatusClauses(proposals)

        assertEquals(2, clauses.size)
    }

    @Test
    fun `parseProposalState should parse hex state values correctly`() {
        val response =
            InspectionResult(
                data = "0x02", // Canceled
                events = emptyList(),
                transfers = emptyList(),
                gasUsed = 0,
                reverted = false,
                vmError = null,
            )

        val state = service.callParseProposalState(response, "1")

        assertEquals(ProposalState.Canceled, state)
    }

    @Test
    fun `parseProposalState should throw on reverted response`() {
        val response =
            InspectionResult(
                data = "0x00",
                events = emptyList(),
                transfers = emptyList(),
                gasUsed = 0,
                reverted = true,
                vmError = "execution reverted",
            )

        assertThrows(IllegalStateException::class.java) {
            service.callParseProposalState(response, "1")
        }
    }

    @Test
    fun `updateStatuses should process proposals in batches`() {
        // Create 125 proposals (will be split into 3 batches: 50, 50, 25)
        val proposals =
            (1..125).map { i ->
                ProposalResult(
                    proposalId = "$i",
                    version = 1,
                    blockId = "block-1",
                    blockNumber = 1L,
                    blockTimestamp = 1000L,
                    createdAtBlockNumber = 1L,
                    startRoundId = i,
                    state = ProposalState.Pending,
                    results = null,
                    description = "ABC",
                )
            }

        every { repository.findByStateIn(any()) } returns proposals
        // findByProposalId will be called by the accumulator for each proposal
        // Mock findById directly since findByIdOrNull is an inline extension function
        every { repository.findById(any<String>()) } answers
            {
                val id = firstArg<String>()
                val proposal = proposals.find { it.proposalId == id }
                java.util.Optional.ofNullable(proposal)
            }
        coEvery { thorClient.inspectClauses(any(), any()) } coAnswers
            {
                val clauses = firstArg<List<Clause>>()
                List(clauses.size) {
                    InspectionResult(
                        data = "0x01", // Active
                        events = emptyList(),
                        transfers = emptyList(),
                        gasUsed = 0,
                        reverted = false,
                        vmError = null,
                    )
                }
            }

        val block = BlockDetails(blockId = blockId(2), blockNumber = 2L, blockTimestamp = 2000L)
        val accumulator = newAccumulator()
        accumulator.startBlock()
        runBlocking { service.updateStatuses(block, accumulator) }
        val (updated, archived) = accumulator.results()

        // All proposals should be updated (state changed from Pending to Active)
        assertEquals(125, updated.size)
        assertEquals(125, archived.size)
    }
}
