package org.vechain.indexer.vevote

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Instant
import java.util.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.utils.BlockDetails
import org.vechain.indexer.utils.IdUtils.generateId
import org.vechain.indexer.utils.ParamUtils.getAsBigDecimal
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString

class VeVoteResultServiceTest {
    @MockK lateinit var repository: VeVoteProposalResultRepository

    @MockK
    lateinit var veVoteProposalResultArchive:
        ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>

    @MockK lateinit var pruner: TargetedPruner<VeVoteProposalResult, VeVoteProposalResultArchive>

    private lateinit var service: TestableService

    private class TestableService(
        repository: VeVoteProposalResultRepository,
        veVoteProposalResultArchive:
            ArchiveService<VeVoteProposalResult, VeVoteProposalResultArchive>,
        pruner: TargetedPruner<VeVoteProposalResult, VeVoteProposalResultArchive>,
    ) : VeVoteResultService(repository, veVoteProposalResultArchive, pruner) {

        fun callCreateOrUpdateExisting(
            blockDetails: BlockDetails,
            events: List<IndexedEvent>,
            existing: VeVoteProposalResult?,
            version: Int,
        ): VeVoteProposalResult = createOrUpdateExisting(blockDetails, events, existing, version)
    }

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = TestableService(repository, veVoteProposalResultArchive, pruner)
    }

    @Test
    fun `processEvents creates a new event when there is no existing event`() {
        val proposalId = "proposal-1"
        val support = BigInteger.ONE // FOR
        val weight = BigDecimal("10")
        val id = generateId(proposalId, Support.FOR.name)

        val event = indexedEvent(proposalId, support, weight)

        every { repository.findById(id) } returns Optional.empty()

        val (updated, existing) = service.processEvents(listOf(event))

        assertEquals(1, updated.size)
        val result = updated.first()
        assertEquals(id, result.id)
        assertEquals(Support.FOR, result.support)
        assertEquals(BigDecimal("10"), result.totalWeight)
        assertEquals(1, result.totalVoters)
        assertEquals(proposalId, result.proposalId)
        assertEquals(event.blockNumber, result.blockNumber)
        assertEquals(event.blockTimestamp, result.blockTimestamp)

        assertEquals(0, existing.size)
    }

    @Test
    fun `processEvents should update and archive if existing record`() {
        val proposalId = "proposal-2"
        val support = BigInteger.ZERO // AGAINST
        val weight = BigDecimal("5")
        val id = generateId(proposalId, Support.AGAINST.name)

        val event = indexedEvent(proposalId, support, weight)

        val existingRecord =
            VeVoteProposalResult(
                id = id,
                version = 1,
                proposalId = proposalId,
                support = Support.AGAINST,
                totalWeight = BigDecimal("15"),
                totalVoters = 3,
                blockId = "block-100",
                blockNumber = 40,
                blockTimestamp = Instant.now().epochSecond - 1000,
            )

        every { repository.findById(id) } returns Optional.of(existingRecord)

        val (updated, existing) = service.processEvents(listOf(event))

        assertEquals(1, updated.size)
        val result = updated.first()
        assertEquals(id, result.id)
        assertEquals(Support.AGAINST, result.support)
        assertEquals(BigDecimal("20"), result.totalWeight) // 15 + 5
        assertEquals(4, result.totalVoters) // 3 + 1
        assertEquals(proposalId, result.proposalId)
        assertEquals(event.blockNumber, result.blockNumber)
        assertEquals(event.blockTimestamp, result.blockTimestamp)

        assertEquals(1, existing.size)
        assertEquals(existingRecord, existing.first())
    }

    @Test
    fun `processEvents should only create one archive record is multiple updates happen in the same block`() {
        val proposalId = "proposal-3"
        val support = BigInteger.TWO // ABSTAIN
        val weight1 = BigDecimal("3")
        val weight2 = BigDecimal("7")
        val id = generateId(proposalId, Support.ABSTAIN.name)

        val event1 = indexedEvent(proposalId, support, weight1, blockNumber = 50)
        val event2 = indexedEvent(proposalId, support, weight2, blockNumber = 50)

        val existingRecord =
            VeVoteProposalResult(
                id = id,
                version = 1,
                proposalId = proposalId,
                support = Support.ABSTAIN,
                totalWeight = BigDecimal("10"),
                totalVoters = 2,
                blockId = "block-200",
                blockNumber = 45,
                blockTimestamp = Instant.now().epochSecond - 2000,
            )

        every { repository.findById(id) } returns Optional.of(existingRecord)

        val (updated, existing) = service.processEvents(listOf(event1, event2))

        assertEquals(1, updated.size)
        val result = updated.first()
        assertEquals(id, result.id)
        assertEquals(Support.ABSTAIN, result.support)
        assertEquals(BigDecimal("20"), result.totalWeight) // 10 + 3 + 7
        assertEquals(4, result.totalVoters) // 2 + 1 + 1
        assertEquals(proposalId, result.proposalId)
        assertEquals(event2.blockNumber, result.blockNumber)
        assertEquals(event2.blockTimestamp, result.blockTimestamp)

        assertEquals(1, existing.size)
        assertEquals(existingRecord, existing.first())
    }

    @Test
    fun `processEvents should create two archive record is multiple updates happen in the different blocks`() {
        val proposalId = "proposal-4"
        val support = BigInteger.ONE // FOR
        val weight1 = BigDecimal("4")
        val weight2 = BigDecimal("6")
        val id = generateId(proposalId, Support.FOR.name)

        val event1 =
            indexedEvent(proposalId, support, weight1, blockNumber = 60, blockId = "block-60")
        val event2 =
            indexedEvent(proposalId, support, weight2, blockNumber = 61, blockId = "block-61")

        val existingRecord =
            VeVoteProposalResult(
                id = id,
                version = 1,
                proposalId = proposalId,
                support = Support.FOR,
                totalWeight = BigDecimal("20"),
                totalVoters = 5,
                blockId = "block-300",
                blockNumber = 55,
                blockTimestamp = Instant.now().epochSecond - 3000,
            )

        every { repository.findById(id) } returns Optional.of(existingRecord)

        val (updated, existing) = service.processEvents(listOf(event1, event2))

        assertEquals(1, updated.size)
        val result = updated.first()
        assertEquals(id, result.id)
        assertEquals(Support.FOR, result.support)
        assertEquals(BigDecimal("30"), result.totalWeight) // 20 + 4 + 6
        assertEquals(7, result.totalVoters) // 5 + 1 + 1
        assertEquals(proposalId, result.proposalId)
        assertEquals(event2.blockNumber, result.blockNumber)
        assertEquals(event2.blockTimestamp, result.blockTimestamp)

        assertEquals(2, existing.size)
        assertEquals(existingRecord, existing[0])
        // The first event should create an archive record
        assertEquals(
            VeVoteProposalResult(
                id = id,
                version = 2,
                proposalId = proposalId,
                support = Support.FOR,
                totalWeight = BigDecimal("24"), // 20 + 4
                totalVoters = 6, // 5 + 1
                blockId = event1.blockId,
                blockNumber = event1.blockNumber,
                blockTimestamp = event1.blockTimestamp,
            ),
            existing[1],
        )
    }

    @Test
    fun `createOrUpdateExisting with no existing record creates new one`() {
        val proposalId = "proposal-5"
        val support = BigInteger.ONE // FOR
        val weight = BigDecimal("12")
        val event =
            indexedEvent(proposalId, support, weight, blockNumber = 70, blockId = "block-70")

        val result =
            service.callCreateOrUpdateExisting(
                BlockDetails(
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                ),
                listOf(event),
                null,
                version = 1,
            )

        val id = generateId(proposalId, Support.FOR.name)
        assertEquals(id, result.id)
        assertEquals(1, result.version)
        assertEquals(proposalId, result.proposalId)
        assertEquals(Support.FOR, result.support)
        assertEquals(BigDecimal("12"), result.totalWeight)
        assertEquals(1, result.totalVoters)
        assertEquals(event.blockId, result.blockId)
        assertEquals(event.blockNumber, result.blockNumber)
        assertEquals(event.blockTimestamp, result.blockTimestamp)
    }

    @Test
    fun `createOrUpdateExisting with existing record updates it`() {
        val proposalId = "proposal-6"
        val support = BigInteger.ZERO // AGAINST
        val weight = BigDecimal("8")
        val event =
            indexedEvent(proposalId, support, weight, blockNumber = 80, blockId = "block-80")

        val id = generateId(proposalId, Support.AGAINST.name)
        val existingRecord =
            VeVoteProposalResult(
                id = id,
                version = 3,
                proposalId = proposalId,
                support = Support.AGAINST,
                totalWeight = BigDecimal("16"),
                totalVoters = 4,
                blockId = "block-400",
                blockNumber = 75,
                blockTimestamp = Instant.now().epochSecond - 4000,
            )

        val result =
            service.callCreateOrUpdateExisting(
                BlockDetails(
                    blockId = event.blockId,
                    blockNumber = event.blockNumber,
                    blockTimestamp = event.blockTimestamp,
                ),
                listOf(event),
                existingRecord,
                version = existingRecord.version + 1,
            )

        assertEquals(id, result.id)
        assertEquals(4, result.version) // Incremented version
        assertEquals(proposalId, result.proposalId)
        assertEquals(Support.AGAINST, result.support)
        assertEquals(BigDecimal("24"), result.totalWeight) // 16 + 8
        assertEquals(5, result.totalVoters) // 4 + 1
        assertEquals(event.blockId, result.blockId)
        assertEquals(event.blockNumber, result.blockNumber)
        assertEquals(event.blockTimestamp, result.blockTimestamp)
    }

    @Test
    fun `createOrUpdateExisting throws if events have different blockId, blockNumber, or proposalId`() {
        val event1 = indexedEvent("proposal-7", BigInteger.ONE, BigDecimal("1"), blockNumber = 90)
        val event2 = indexedEvent("proposal-7", BigInteger.ONE, BigDecimal("1"), blockNumber = 91)
        val event3 = indexedEvent("proposal-8", BigInteger.ONE, BigDecimal("1"), blockNumber = 90)

        val blockDetails =
            BlockDetails(
                blockId = event1.blockId,
                blockNumber = event1.blockNumber,
                blockTimestamp = event1.blockTimestamp,
            )

        // Different block numbers
        try {
            service.callCreateOrUpdateExisting(
                blockDetails,
                listOf(event1, event2),
                null,
                version = 1,
            )
            throw AssertionError("Expected an exception due to different block numbers")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Different proposalIds
        try {
            service.callCreateOrUpdateExisting(
                blockDetails,
                listOf(event1, event3),
                null,
                version = 1,
            )
            throw AssertionError("Expected an exception due to different proposalIds")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `createOrUpdateExisting throws if events have different support values`() {
        val event1 = indexedEvent("proposal-9", BigInteger.ONE, BigDecimal("1"))
        val event2 = indexedEvent("proposal-9", BigInteger.ZERO, BigDecimal("1"))

        val blockDetails =
            BlockDetails(
                blockId = event1.blockId,
                blockNumber = event1.blockNumber,
                blockTimestamp = event1.blockTimestamp,
            )

        try {
            service.callCreateOrUpdateExisting(
                blockDetails,
                listOf(event1, event2),
                null,
                version = 1,
            )
            throw AssertionError("Expected an exception due to different support values")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }

    private fun indexedEvent(
        proposalId: String,
        support: BigInteger,
        weight: BigDecimal,
        blockId: String = "block-123",
        blockNumber: Long = 42,
        blockTimestamp: Long = Instant.now().epochSecond,
    ): IndexedEvent {
        val params =
            mockk<AbiEventParameters> {
                every { getAsString("proposalId") } returns proposalId
                every { getAsBigInteger("support") } returns support
                every { getAsBigDecimal("weight") } returns weight
            }

        return IndexedEvent(
            id = UUID.randomUUID().toString(),
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            txId = "0xtx",
            origin = null,
            paid = null,
            gasUsed = null,
            gasPayer = null,
            raw = null,
            params = params,
            address = null,
            eventType = "VoteCast",
            clauseIndex = 0,
            signature = null,
        )
    }
}
