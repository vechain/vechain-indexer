package org.vechain.indexer.b3tr.xAlloc

import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import java.math.BigDecimal
import java.math.BigInteger
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.thor.HexUtils.toHex
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.BlockUnexpanded
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class XAllocResultServiceTest {
    @MockK lateinit var repository: XAllocResultWriteRepository

    @MockK lateinit var thorClient: ThorClient

    private lateinit var service: TestableXAllocResultService

    private val app1 = "0x" + "11".repeat(32)
    private val app2 = "0x" + "22".repeat(32)

    private fun blockId(num: Long): String = toHex(num, 64)

    /** Opens the three protected builders, which carry the accumulation rules. */
    private inner class TestableXAllocResultService(
        repository: XAllocResultWriteRepository,
        thorClient: ThorClient,
    ) : XAllocResultService(repository, thorClient, "0x1234567890abcdef") {
        fun voteResult(
            blockDetails: BlockDetails,
            existing: XAllocResult?,
            voters: Long,
            votesReceived: BigInteger,
        ) = addOrCreateVoteResult(1, app1, blockDetails, existing, voters, votesReceived)

        fun claimResult(
            blockDetails: BlockDetails,
            existing: XAllocResult?,
            total: String,
            unallocated: String,
            team: String,
            rewards: String,
        ) =
            addOrCreateRewardClaimResult(
                1,
                app1,
                blockDetails,
                existing,
                BigDecimal(total),
                BigDecimal(unallocated),
                BigDecimal(team),
                BigDecimal(rewards),
            )

        fun dbaResult(blockDetails: BlockDetails, existing: XAllocResult?, amount: String) =
            addOrCreateDbaFundResult(1, app1, blockDetails, existing, BigDecimal(amount))
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { repository.findCurrent(any(), any()) } returns emptyList()
        coEvery { thorClient.getBlockUnexpanded(BlockRevision.Keyword.BEST) } returns
            mockk<BlockUnexpanded> { every { id } returns blockId(999) }
        // Quadratic funding reads as disabled, so a vote weight counts as cast.
        coEvery { thorClient.inspectClauses(any(), any()) } returns
            listOf(
                InspectionResult(
                    vmError = null,
                    data = "0x" + "0".repeat(63) + "1",
                    reverted = false,
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                )
            )
        service = TestableXAllocResultService(repository, thorClient)
    }

    private fun block(number: Long) =
        BlockDetails(blockId(number), number, blockTimestamp = number * 10)

    private fun result(
        roundId: Int = 1,
        appId: String = app1,
        blockNumber: Long = 1L,
        voters: Long = 0,
        votesReceived: BigInteger = BigInteger.ZERO,
        totalAmount: BigDecimal? = null,
    ) =
        XAllocResult(
            blockId = blockId(blockNumber),
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            roundId = roundId,
            appId = appId,
            voters = voters,
            votesReceived = votesReceived,
            totalAmount = totalAmount,
        )

    private fun voteEvent(
        roundId: Int,
        appId: String,
        weight: Long,
        blockNumber: Long,
    ): IndexedEvent =
        buildIndexedEvent(
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            blockId = blockId(blockNumber),
            eventType = "B3TR_XAllocationVote",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "roundId" to roundId,
                            "appsIds" to listOf(appId),
                            "voteWeights" to listOf(BigInteger.valueOf(weight)),
                        )
                ),
        )

    private fun claimEvent(roundId: Int, appId: String, blockNumber: Long): IndexedEvent =
        buildIndexedEvent(
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            blockId = blockId(blockNumber),
            eventType = "B3TR_XAllocationRewardsClaimed",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf(
                            "roundId" to roundId,
                            "appId" to appId,
                            "totalAmount" to BigInteger("1500000000000000000"),
                            "unallocatedAmount" to BigInteger("250000000000000000"),
                            "teamAllocationAmount" to BigInteger("500000000000000000"),
                            "rewardsAllocationAmount" to BigInteger("750000000000000000"),
                        )
                ),
        )

    private fun dbaEvent(roundId: Int, appId: String, blockNumber: Long): IndexedEvent =
        buildIndexedEvent(
            blockNumber = blockNumber,
            blockTimestamp = blockNumber * 10,
            blockId = blockId(blockNumber),
            eventType = "B3TR_DBAFundsDistributed",
            params =
                AbiEventParameters(
                    returnValues =
                        mapOf("roundId" to roundId, "appId" to appId, "amount" to BigInteger.TEN)
                ),
        )

    private fun process(vararg events: IndexedEvent): List<XAllocResult> = runBlocking {
        service.processEvents(events.toList())
    }

    @Test
    fun `a vote result starts at the event's totals and then accumulates them`() {
        val created = service.voteResult(block(1), null, voters = 5, BigInteger.valueOf(100))

        assertEquals(5, created.voters)
        assertEquals(BigInteger.valueOf(100), created.votesReceived)
        assertNull(created.totalAmount)

        val accumulated = service.voteResult(block(5), created, voters = 2, BigInteger.valueOf(30))

        assertEquals(7, accumulated.voters)
        assertEquals(BigInteger.valueOf(130), accumulated.votesReceived)
        assertEquals(5L, accumulated.blockNumber)
    }

    @Test
    fun `a claim result adds each of the four amounts and leaves the vote totals alone`() {
        val created = service.claimResult(block(1), null, "10.0", "2.0", "3.0", "5.0")

        assertEquals(0, created.voters)
        assertEquals(BigDecimal("10.0"), created.totalAmount)

        val existing = created.copy(voters = 3, votesReceived = BigInteger.valueOf(75))
        val accumulated = service.claimResult(block(2), existing, "3.0", "0.5", "1.0", "1.5")

        assertEquals(3, accumulated.voters)
        assertEquals(BigDecimal("13.0"), accumulated.totalAmount)
        assertEquals(BigDecimal("2.5"), accumulated.unallocatedAmount)
        assertEquals(BigDecimal("4.0"), accumulated.teamAllocationAmount)
        assertEquals(BigDecimal("6.5"), accumulated.rewardsAllocationAmount)
    }

    @Test
    fun `a DBA distribution adds to the total amount alone, from null or from a figure`() {
        assertEquals(BigDecimal("7.5"), service.dbaResult(block(1), null, "7.5").totalAmount)
        assertEquals(
            BigDecimal("5.0"),
            service.dbaResult(block(1), result(voters = 1), "5.0").totalAmount,
        )
        assertEquals(
            BigDecimal("3.5"),
            service.dbaResult(block(8), result(totalAmount = BigDecimal("2.0")), "1.5").totalAmount,
        )
    }

    @Test
    fun `each round and app gets its own row`() {
        val rows =
            process(
                voteEvent(1, app1, 2, blockNumber = 5),
                voteEvent(1, app2, 3, blockNumber = 5),
                voteEvent(2, app1, 4, blockNumber = 5),
            )

        assertEquals(
            mapOf(
                (1 to app1) to BigInteger.TWO,
                (1 to app2) to BigInteger.valueOf(3),
                (2 to app1) to BigInteger.valueOf(4),
            ),
            rows.associate { (it.roundId to it.appId) to it.votesReceived },
        )
    }

    @Test
    fun `votes in one block are aggregated into a single row`() {
        val rows = process(voteEvent(1, app1, 5, blockNumber = 10), voteEvent(1, app1, 7, 10))

        assertEquals(1, rows.size)
        assertEquals(2, rows.single().voters)
        assertEquals(BigInteger.valueOf(12), rows.single().votesReceived)
    }

    @Test
    fun `a running total across blocks writes the row each block reaches`() {
        val rows = process(voteEvent(1, app1, 3, blockNumber = 1), voteEvent(1, app1, 4, 2))

        assertEquals(listOf(1L, 2L), rows.map { it.blockNumber })
        assertEquals(
            listOf(BigInteger.valueOf(3), BigInteger.valueOf(7)),
            rows.map { it.votesReceived },
        )
    }

    @Test
    fun `the stored row is the base of the first block's total`() {
        every { repository.findCurrent(setOf(1), setOf(app1)) } returns
            listOf(result(voters = 1, votesReceived = BigInteger.ONE))

        val row = process(voteEvent(1, app1, 10, blockNumber = 2)).single()

        assertEquals(2, row.voters)
        assertEquals(BigInteger.valueOf(11), row.votesReceived)
        assertEquals(2L, row.blockNumber)
    }

    @Test
    fun `a vote, a claim and a distribution in one block collapse into one row`() {
        val row =
            process(
                    voteEvent(4, app1, 50, blockNumber = 15),
                    claimEvent(4, app1, blockNumber = 15),
                    dbaEvent(4, app1, blockNumber = 15),
                )
                .single()

        assertEquals(1, row.voters)
        assertEquals(BigInteger.valueOf(50), row.votesReceived)
        // 1.5 from the claim plus the distribution's 10 wei.
        assertEquals(BigDecimal("1.500000000000000010"), row.totalAmount)
        assertEquals(BigDecimal("0.250000000000000000"), row.unallocatedAmount)
        assertEquals(BigDecimal("0.500000000000000000"), row.teamAllocationAmount)
        assertEquals(BigDecimal("0.750000000000000000"), row.rewardsAllocationAmount)
        assertEquals(15L, row.blockNumber)
    }

    @Test
    fun `many events across five blocks write one row per block, not one per event`() {
        val events =
            (1..5).flatMap { block ->
                (1..4).map { voteEvent(1, app1, 1, blockNumber = block.toLong()) }
            }

        val rows = process(*events.toTypedArray())

        assertEquals((1L..5L).toList(), rows.map { it.blockNumber })
        assertEquals(BigInteger.valueOf(20), rows.last().votesReceived)
    }

    @Test
    fun `no events yield no rows`() {
        assertEquals(emptyList<XAllocResult>(), process())
    }
}
