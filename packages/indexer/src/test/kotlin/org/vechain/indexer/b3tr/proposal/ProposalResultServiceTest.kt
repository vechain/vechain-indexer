package org.vechain.indexer.b3tr.proposal

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.Pruner
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.action.IdUtils.generateId
import org.vechain.indexer.b3tr.proposal.repository.ProposalResultRepository
import org.vechain.indexer.b3tr.voting.Support
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class ProposalResultServiceTest {
    @MockK lateinit var repository: ProposalResultRepository

    @MockK
    lateinit var proposalResultArchiveService: ArchiveService<ProposalResult, ProposalResultArchive>

    @MockK lateinit var pruner: Pruner

    private lateinit var service: ProposalResultService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = ProposalResultService(repository, proposalResultArchiveService, pruner)
    }

    @Test
    fun `processEvents should update if no existing records`() {
        // Create two events for the same tokenId, different blockNumbers
        val tokenId = "token-1"
        val event1 =
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
                                "support" to 1,
                                "voteWeight" to BigInteger.TEN,
                                "votePower" to BigInteger.ONE,
                            )
                    ),
            )
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 2L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account2",
                                "proposalId" to "proposal2",
                                "support" to 0,
                                "voteWeight" to BigInteger.ONE,
                                "votePower" to BigInteger.ZERO,
                            )
                    ),
            )

        // Mock repository to return null for first lookup
        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Should be two new updated records and no archives
        assertEquals(2, updated.size)
        assertEquals(0, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals("proposal2", updated[1].proposalId)
    }

    @Test
    fun `processEvents should update and archive if existing record`() {
        // Create two events for the same tokenId, different blockNumbers
        val tokenId = "token-1"
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 1,
                                "voteWeight" to BigInteger.TEN,
                                "votePower" to BigInteger.ONE,
                            )
                    ),
            )

        // Mock repository to return an existing record for the first event
        val existingRecord =
            ProposalResult(
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                proposalId = "proposal1",
                voters = 1L,
                totalWeight = BigInteger.TEN,
                totalPower = BigInteger.ONE,
                support = Support.FOR,
            )

        every { repository.findByIdOrNull(generateId("proposal1", Support.FOR.name)) } returns
            existingRecord

        val (updated, archived) = service.processEvents(listOf(event1))

        // Should be one updated record and one archived record
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(2L, updated[0].voters)
        assertEquals(BigInteger.valueOf(20), updated[0].totalWeight)
        assertEquals(BigInteger.valueOf(2), updated[0].totalPower)
        assertEquals(Support.FOR, updated[0].support)
        assertEquals("proposal1", archived[0].proposalId)
        assertEquals(1, archived[0].voters)
        assertEquals(BigInteger.TEN, archived[0].totalWeight)
        assertEquals(BigInteger.ONE, archived[0].totalPower)
        assertEquals(Support.FOR, archived[0].support)
    }

    @Test
    fun `processEvents should only create one archive record is multiple updates happen in the same block`() {
        // Create two events for the same proposalId in the same block
        val event1 =
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
                                "support" to 1,
                                "voteWeight" to BigInteger.TEN,
                                "votePower" to BigInteger.ONE,
                            )
                    ),
            )
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 1L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account2",
                                "proposalId" to "proposal1",
                                "support" to 1,
                                "voteWeight" to BigInteger.ONE,
                                "votePower" to BigInteger.TWO,
                            )
                    ),
            )

        // Mock repository to return null for first lookup
        val existingRecord =
            ProposalResult(
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                proposalId = "proposal1",
                voters = 1L,
                totalWeight = BigInteger.TEN,
                totalPower = BigInteger.ONE,
                support = Support.FOR,
            )

        every { repository.findByIdOrNull(any()) } returns existingRecord

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Should be one new updated records and one archive record
        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(3L, updated[0].voters)
        assertEquals(BigInteger.valueOf(21), updated[0].totalWeight)
        assertEquals(BigInteger.valueOf(4), updated[0].totalPower)
        assertEquals(existingRecord, archived[0])
    }

    @Test
    fun `processEvents should create two archive record is multiple updates happen in the different blocks`() {
        // Create two events for the same proposalId in different blocks
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockId = "block-1",
                blockNumber = 1L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account1",
                                "proposalId" to "proposal1",
                                "support" to 1,
                                "voteWeight" to BigInteger.TEN,
                                "votePower" to BigInteger.ONE,
                            )
                    ),
            )
        val event2 =
            buildIndexedEvent(
                id = "e2",
                blockId = "block-2",
                blockNumber = 2L,
                eventType = "B3TR_ProposalVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to "account2",
                                "proposalId" to "proposal1",
                                "support" to 1,
                                "voteWeight" to BigInteger.ONE,
                                "votePower" to BigInteger.TWO,
                            )
                    ),
            )

        // Mock repository to return null for first lookup
        val existingRecord =
            ProposalResult(
                version = 1,
                blockId = "block-1",
                blockNumber = 1L,
                blockTimestamp = 1000L,
                proposalId = "proposal1",
                voters = 1L,
                totalWeight = BigInteger.TEN,
                totalPower = BigInteger.ONE,
                support = Support.FOR,
            )

        every { repository.findByIdOrNull(any()) } returns existingRecord

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Should be one new updated records and two archive records
        assertEquals(1, updated.size)
        assertEquals(2, archived.size)
        assertEquals("proposal1", updated[0].proposalId)
        assertEquals(3L, updated[0].voters)
        assertEquals(BigInteger.valueOf(21), updated[0].totalWeight)
        assertEquals(BigInteger.valueOf(4), updated[0].totalPower)
        assertEquals(existingRecord, archived[0])
    }
}
