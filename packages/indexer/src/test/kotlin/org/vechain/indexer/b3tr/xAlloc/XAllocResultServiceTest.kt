package org.vechain.indexer.b3tr.xAlloc

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigDecimal
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.repository.findByIdOrNull
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.b3tr.xAlloc.repository.XAllocResultRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.pruner.TargetedPruner
import org.vechain.indexer.rest.ExecuteCodeResponse
import org.vechain.indexer.thor.ThorService

@ExtendWith(MockKExtension::class)
internal class XAllocResultServiceTest {
    @MockK lateinit var repository: XAllocResultRepository

    @MockK lateinit var archiveService: ArchiveService<XAllocResult, XAllocResultArchive>

    @MockK lateinit var pruner: TargetedPruner<XAllocResult, XAllocResultArchive>

    @MockK lateinit var mongoTemplate: MongoTemplate

    @MockK lateinit var thorService: ThorService

    private val xAllocPoolContract = "0x1234567890abcdef"

    private lateinit var service: XAllocResultService

    // Test wrapper to access protected functions directly
    private inner class TestableXAllocResultService(
        repository: XAllocResultRepository,
        archiveService: ArchiveService<XAllocResult, XAllocResultArchive>,
        pruner: TargetedPruner<XAllocResult, XAllocResultArchive>,
        thorService: ThorService,
        xAllocPoolContract: String,
    ) :
        XAllocResultService(
            repository,
            archiveService,
            pruner,
            thorService,
            xAllocPoolContract,
            mongoTemplate,
        ) {
        fun testAddOrCreateVoteResult(
            roundId: Int,
            appId: String,
            blockDetails: org.vechain.indexer.utils.BlockDetails,
            existing: XAllocResult?,
            voters: Long,
            votesReceived: BigInteger,
        ): XAllocResult =
            addOrCreateVoteResult(roundId, appId, blockDetails, existing, voters, votesReceived)

        fun testAddOrCreateRewardClaimResult(
            roundId: Int,
            appId: String,
            blockDetails: org.vechain.indexer.utils.BlockDetails,
            existing: XAllocResult?,
            totalAmount: BigDecimal,
            unallocatedAmount: BigDecimal,
            teamAllocationAmount: BigDecimal,
            rewardsAllocationAmount: BigDecimal,
        ): XAllocResult =
            addOrCreateRewardClaimResult(
                roundId,
                appId,
                blockDetails,
                existing,
                totalAmount,
                unallocatedAmount,
                teamAllocationAmount,
                rewardsAllocationAmount,
            )

        fun testAddOrCreateDbaFundResult(
            roundId: Int,
            appId: String,
            blockDetails: org.vechain.indexer.utils.BlockDetails,
            existing: XAllocResult?,
            amount: BigDecimal,
        ): XAllocResult = addOrCreateDbaFundResult(roundId, appId, blockDetails, existing, amount)
    }

    private lateinit var testableService: TestableXAllocResultService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        // Mock thorService to return that quadratic funding is disabled (true = disabled, so QF is
        // NOT used)
        every { thorService.executeReadOnlyCode(any()) } returns
            listOf(
                ExecuteCodeResponse(
                    vmError = null,
                    data =
                        "0x0000000000000000000000000000000000000000000000000000000000000001", // represents true (disabled = not enabled)
                    reverted = false,
                    events = emptyList(),
                    transfers = emptyList(),
                    gasUsed = 0,
                )
            )
        service =
            XAllocResultService(
                repository,
                archiveService,
                pruner,
                thorService,
                xAllocPoolContract,
                mongoTemplate,
            )
        testableService =
            TestableXAllocResultService(
                repository,
                archiveService,
                pruner,
                thorService,
                xAllocPoolContract,
            )
    }

    private fun createBlockDetails(
        blockId: String = "block-1",
        blockNumber: Long = 1L,
        blockTimestamp: Long = 100L,
    ): org.vechain.indexer.utils.BlockDetails =
        org.vechain.indexer.utils.BlockDetails(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
        )

    // ===== Unit tests for addOrCreateVoteResult =====

    @Test
    fun `addOrCreateVoteResult creates new record when existing is null`() {
        val blockDetails = createBlockDetails()
        val result =
            testableService.testAddOrCreateVoteResult(
                roundId = 1,
                appId = "app1",
                blockDetails = blockDetails,
                existing = null,
                voters = 5,
                votesReceived = BigInteger.valueOf(100),
            )

        assertEquals(1, result.version)
        assertEquals("app1", result.appId)
        assertEquals(1, result.roundId)
        assertEquals(5, result.voters)
        assertEquals(BigInteger.valueOf(100), result.votesReceived)
        assertEquals("block-1", result.blockId)
        assertEquals(1L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
        // Allocation amounts should be null when creating new
        assertEquals(null, result.totalAmount)
        assertEquals(null, result.unallocatedAmount)
        assertEquals(null, result.teamAllocationAmount)
        assertEquals(null, result.rewardsAllocationAmount)
    }

    @Test
    fun `addOrCreateVoteResult accumulates voters and votes with existing record`() {
        val blockDetails =
            createBlockDetails(blockId = "block-5", blockNumber = 5L, blockTimestamp = 55L)
        val existing =
            XAllocResult(
                version = 2,
                blockId = "block-2",
                blockNumber = 2L,
                blockTimestamp = 22L,
                roundId = 1,
                appId = "app1",
                voters = 3,
                votesReceived = BigInteger.valueOf(50),
            )

        val result =
            testableService.testAddOrCreateVoteResult(
                roundId = 1,
                appId = "app1",
                blockDetails = blockDetails,
                existing = existing,
                voters = 2,
                votesReceived = BigInteger.valueOf(30),
            )

        assertEquals(3, result.version) // incremented from 2
        assertEquals("app1", result.appId)
        assertEquals(1, result.roundId)
        assertEquals(5, result.voters) // 3 + 2
        assertEquals(BigInteger.valueOf(80), result.votesReceived) // 50 + 30
        assertEquals("block-5", result.blockId) // updated
        assertEquals(5L, result.blockNumber) // updated
        assertEquals(55L, result.blockTimestamp) // updated
    }

    @Test
    fun `addOrCreateVoteResult retains existing allocation amounts`() {
        val blockDetails =
            createBlockDetails(blockId = "block-5", blockNumber = 5L, blockTimestamp = 55L)
        val existing =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 1,
                appId = "app1",
                voters = 2,
                votesReceived = BigInteger.valueOf(50),
                totalAmount = BigDecimal("5.000000000000000000"),
                unallocatedAmount = BigDecimal("1.000000000000000000"),
                teamAllocationAmount = BigDecimal("1.500000000000000000"),
                rewardsAllocationAmount = BigDecimal("2.500000000000000000"),
            )

        val result =
            testableService.testAddOrCreateVoteResult(
                roundId = 1,
                appId = "app1",
                blockDetails = blockDetails,
                existing = existing,
                voters = 1,
                votesReceived = BigInteger.valueOf(30),
            )

        assertEquals(2, result.version)
        assertEquals(3, result.voters) // 2 + 1
        assertEquals(BigInteger.valueOf(80), result.votesReceived) // 50 + 30
        // Allocation amounts should be retained from existing
        assertEquals(BigDecimal("5.000000000000000000"), result.totalAmount)
        assertEquals(BigDecimal("1.000000000000000000"), result.unallocatedAmount)
        assertEquals(BigDecimal("1.500000000000000000"), result.teamAllocationAmount)
        assertEquals(BigDecimal("2.500000000000000000"), result.rewardsAllocationAmount)
    }

    // ===== Unit tests for addOrCreateRewardClaimResult =====

    @Test
    fun `addOrCreateRewardClaimResult creates new record when existing is null`() {
        val blockDetails = createBlockDetails()
        val result =
            testableService.testAddOrCreateRewardClaimResult(
                roundId = 2,
                appId = "claimApp",
                blockDetails = blockDetails,
                existing = null,
                totalAmount = BigDecimal("10.0"),
                unallocatedAmount = BigDecimal("2.0"),
                teamAllocationAmount = BigDecimal("3.0"),
                rewardsAllocationAmount = BigDecimal("5.0"),
            )

        assertEquals(1, result.version)
        assertEquals("claimApp", result.appId)
        assertEquals(2, result.roundId)
        assertEquals(0, result.voters)
        assertEquals(BigInteger.ZERO, result.votesReceived)
        assertEquals(BigDecimal("10.0"), result.totalAmount)
        assertEquals(BigDecimal("2.0"), result.unallocatedAmount)
        assertEquals(BigDecimal("3.0"), result.teamAllocationAmount)
        assertEquals(BigDecimal("5.0"), result.rewardsAllocationAmount)
    }

    @Test
    fun `addOrCreateRewardClaimResult accumulates allocation amounts`() {
        val blockDetails =
            createBlockDetails(blockId = "block-10", blockNumber = 10L, blockTimestamp = 100L)
        val existing =
            XAllocResult(
                version = 1,
                blockId = "block-5",
                blockNumber = 5L,
                blockTimestamp = 50L,
                roundId = 2,
                appId = "claimApp",
                voters = 3,
                votesReceived = BigInteger.valueOf(75),
                totalAmount = BigDecimal("5.0"),
                unallocatedAmount = BigDecimal("1.0"),
                teamAllocationAmount = BigDecimal("1.5"),
                rewardsAllocationAmount = BigDecimal("2.5"),
            )

        val result =
            testableService.testAddOrCreateRewardClaimResult(
                roundId = 2,
                appId = "claimApp",
                blockDetails = blockDetails,
                existing = existing,
                totalAmount = BigDecimal("3.0"),
                unallocatedAmount = BigDecimal("0.5"),
                teamAllocationAmount = BigDecimal("1.0"),
                rewardsAllocationAmount = BigDecimal("1.5"),
            )

        assertEquals(2, result.version)
        // Vote data should be retained
        assertEquals(3, result.voters)
        assertEquals(BigInteger.valueOf(75), result.votesReceived)
        // Amounts should be accumulated
        assertEquals(BigDecimal("8.0"), result.totalAmount) // 5.0 + 3.0
        assertEquals(BigDecimal("1.5"), result.unallocatedAmount) // 1.0 + 0.5
        assertEquals(BigDecimal("2.5"), result.teamAllocationAmount) // 1.5 + 1.0
        assertEquals(BigDecimal("4.0"), result.rewardsAllocationAmount) // 2.5 + 1.5
        assertEquals("block-10", result.blockId)
        assertEquals(10L, result.blockNumber)
        assertEquals(100L, result.blockTimestamp)
    }

    // ===== Unit tests for addOrCreateDbaFundResult =====

    @Test
    fun `addOrCreateDbaFundResult creates new record when existing is null`() {
        val blockDetails = createBlockDetails()
        val result =
            testableService.testAddOrCreateDbaFundResult(
                roundId = 3,
                appId = "dbaApp",
                blockDetails = blockDetails,
                existing = null,
                amount = BigDecimal("7.5"),
            )

        assertEquals(1, result.version)
        assertEquals("dbaApp", result.appId)
        assertEquals(3, result.roundId)
        assertEquals(0, result.voters)
        assertEquals(BigInteger.ZERO, result.votesReceived)
        assertEquals(BigDecimal("7.5"), result.totalAmount)
        assertEquals(null, result.unallocatedAmount)
        assertEquals(null, result.teamAllocationAmount)
        assertEquals(null, result.rewardsAllocationAmount)
    }

    @Test
    fun `addOrCreateDbaFundResult accumulates total amount`() {
        val blockDetails =
            createBlockDetails(blockId = "block-8", blockNumber = 8L, blockTimestamp = 88L)
        val existing =
            XAllocResult(
                version = 1,
                blockId = "block-5",
                blockNumber = 5L,
                blockTimestamp = 50L,
                roundId = 3,
                appId = "dbaApp",
                voters = 2,
                votesReceived = BigInteger.valueOf(40),
                totalAmount = BigDecimal("2.0"),
            )

        val result =
            testableService.testAddOrCreateDbaFundResult(
                roundId = 3,
                appId = "dbaApp",
                blockDetails = blockDetails,
                existing = existing,
                amount = BigDecimal("1.5"),
            )

        assertEquals(2, result.version)
        // Vote data should be retained
        assertEquals(2, result.voters)
        assertEquals(BigInteger.valueOf(40), result.votesReceived)
        // Amount should be accumulated
        assertEquals(BigDecimal("3.5"), result.totalAmount) // 2.0 + 1.5
        assertEquals("block-8", result.blockId)
        assertEquals(8L, result.blockNumber)
        assertEquals(88L, result.blockTimestamp)
    }

    @Test
    fun `addOrCreateDbaFundResult with existing null totalAmount`() {
        val blockDetails = createBlockDetails()
        val existing =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 3,
                appId = "dbaApp",
                voters = 1,
                votesReceived = BigInteger.TEN,
                totalAmount = null,
            )

        val result =
            testableService.testAddOrCreateDbaFundResult(
                roundId = 3,
                appId = "dbaApp",
                blockDetails = blockDetails,
                existing = existing,
                amount = BigDecimal("5.0"),
            )

        assertEquals(2, result.version)
        assertEquals(BigDecimal("5.0"), result.totalAmount) // null + 5.0 = 5.0
        assertEquals(1, result.voters)
        assertEquals(BigInteger.TEN, result.votesReceived)
    }

    @Test
    fun `processEvents should create new records if no existing records`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.TEN),
                            )
                    ),
            )

        val event2 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 1L,
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 2,
                                "appsIds" to listOf("app2"),
                                "voteWeights" to listOf(1),
                            )
                    ),
            )

        // Mock repository to return null for first lookup
        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(event1, event2))

        // Should be two new updated records and no archives
        assertEquals(2, updated.size)
        assertEquals(updated[0].version, 1)
        assertEquals(updated[1].version, 1)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents should update a record if exists`() {
        val event1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 2L,
                blockTimestamp = 2L,
                blockId = "block-2",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.TEN),
                            )
                    ),
            )

        // Mock repository to return an existing record
        val existing =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 1,
                appId = "app1",
                voters = 1,
                votesReceived = BigInteger.ONE,
            )
        every { repository.findByIdOrNull(any()) } returns existing

        val (updated, archived) = service.processEvents(listOf(event1))

        // Should be one updated record and one archived
        assertEquals(1, updated.size)
        assertEquals(updated[0].version, 2)
        assertEquals(updated[0].voters, 2)
        assertEquals(updated[0].votesReceived, BigInteger.TEN + BigInteger.ONE)
        assertEquals(updated[0].blockNumber, 2L)
        assertEquals(updated[0].blockId, "block-2")
        assertEquals(updated[0].blockTimestamp, 2L)

        // Check the archived record is unchanged
        assertEquals(1, archived.size)
        assertEquals(archived[0], existing)
    }

    @Test
    fun `processEvents aggregates multiple votes within the same block and round`() {
        val e1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                blockId = "block-10",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(5)),
                            )
                    ),
            )

        val e2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                blockId = "block-10",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(7)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(e1, e2))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(1, u.version)
        assertEquals(2, u.voters)
        assertEquals(BigInteger.valueOf(12), u.votesReceived)
        assertEquals(10L, u.blockNumber)
        assertEquals("block-10", u.blockId)
        assertEquals(1000L, u.blockTimestamp)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents increments version and archives prior snapshot across blocks`() {
        val b1 =
            buildIndexedEvent(
                id = "b1",
                blockNumber = 1L,
                blockTimestamp = 11L,
                blockId = "block-1",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 42,
                                "appsIds" to listOf("myApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(3)),
                            )
                    ),
            )

        val b2 =
            buildIndexedEvent(
                id = "b2",
                blockNumber = 2L,
                blockTimestamp = 22L,
                blockId = "block-2",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 42,
                                "appsIds" to listOf("myApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(4)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(b1, b2))

        // Final updated aggregate
        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(2, u.version)
        assertEquals(2, u.voters)
        assertEquals(BigInteger.valueOf(7), u.votesReceived)
        assertEquals(2L, u.blockNumber)
        assertEquals("block-2", u.blockId)
        assertEquals(22L, u.blockTimestamp)

        // One archived snapshot (the v1 state from block 1)
        assertEquals(1, archived.size)
        val a = archived.first()
        assertEquals(1, a.version)
        assertEquals(1L, a.blockNumber)
        assertEquals("block-1", a.blockId)
        assertEquals(11L, a.blockTimestamp)
        assertEquals(1, a.voters)
        assertEquals(BigInteger.valueOf(3), a.votesReceived)
    }

    @Test
    fun `processEvents creates independent records across rounds and apps`() {
        val e1 =
            buildIndexedEvent(
                id = "e1",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(2)),
                            )
                    ),
            )

        val e2 =
            buildIndexedEvent(
                id = "e2",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 1,
                                "appsIds" to listOf("app2"),
                                "voteWeights" to listOf(BigInteger.valueOf(3)),
                            )
                    ),
            )

        val e3 =
            buildIndexedEvent(
                id = "e3",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 2,
                                "appsIds" to listOf("app1"),
                                "voteWeights" to listOf(BigInteger.valueOf(4)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(e1, e2, e3))

        assertEquals(3, updated.size)
        // All are fresh records
        updated.forEach { assertEquals(1, it.version) }
        // Check values by composing a map (roundId, appId) -> votesReceived
        val totals = updated.associate { Pair(Pair(it.roundId, it.appId), it.votesReceived) }
        assertEquals(BigInteger.valueOf(2), totals[Pair(1, "app1")])
        assertEquals(BigInteger.valueOf(3), totals[Pair(1, "app2")])
        assertEquals(BigInteger.valueOf(4), totals[Pair(2, "app1")])
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents prefers in-memory cache over repository after first lookup`() {
        val b1 =
            buildIndexedEvent(
                id = "b1",
                blockNumber = 100L,
                blockTimestamp = 1001L,
                blockId = "block-100",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 9,
                                "appsIds" to listOf("cacheApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(1)),
                            )
                    ),
            )
        val b2 =
            buildIndexedEvent(
                id = "b2",
                blockNumber = 101L,
                blockTimestamp = 1002L,
                blockId = "block-101",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 9,
                                "appsIds" to listOf("cacheApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(2)),
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(b1, b2))

        assertEquals(1, updated.size)
        assertEquals(1, archived.size)
        // Repository should be consulted only once (first resolution), second lookup uses cache
        verify(exactly = 1) { repository.findByIdOrNull(any()) }
    }

    @Test
    fun `processEvents creates new record from ClaimReward event with allocation amounts`() {
        val claimEvent =
            buildIndexedEvent(
                id = "claim-1",
                blockNumber = 5L,
                blockTimestamp = 55L,
                blockId = "block-5",
                eventType = "B3TR_XAllocationRewardsClaimed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 3,
                                "appId" to "claimApp",
                                "totalAmount" to
                                    BigInteger("1000000000000000000"), // 1 scaled by 10^18
                                "unallocatedAmount" to
                                    BigInteger("200000000000000000"), // 0.2 scaled by 10^18
                                "teamAllocationAmount" to
                                    BigInteger("300000000000000000"), // 0.3 scaled by 10^18
                                "rewardsAllocationAmount" to
                                    BigInteger("500000000000000000"), // 0.5 scaled by 10^18
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(claimEvent))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(1, u.version)
        assertEquals(3, u.roundId)
        assertEquals("claimApp", u.appId)
        assertEquals(0, u.voters) // No voters from claim event
        assertEquals(BigInteger.ZERO, u.votesReceived)
        assertEquals(BigDecimal("1.000000000000000000"), u.totalAmount)
        assertEquals(BigDecimal("0.200000000000000000"), u.unallocatedAmount)
        assertEquals(BigDecimal("0.300000000000000000"), u.teamAllocationAmount)
        assertEquals(BigDecimal("0.500000000000000000"), u.rewardsAllocationAmount)
        assertEquals("block-5", u.blockId)
        assertEquals(5L, u.blockNumber)
        assertEquals(55L, u.blockTimestamp)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents updates existing record with allocation amounts from ClaimReward event`() {
        val existingRecord =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 3,
                appId = "claimApp",
                voters = 5,
                votesReceived = BigInteger.valueOf(100),
            )

        val claimEvent =
            buildIndexedEvent(
                id = "claim-1",
                blockNumber = 10L,
                blockTimestamp = 100L,
                blockId = "block-10",
                eventType = "B3TR_XAllocationRewardsClaimed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 3,
                                "appId" to "claimApp",
                                "totalAmount" to BigInteger("2000000000000000000"), // 2
                                "unallocatedAmount" to BigInteger("400000000000000000"), // 0.4
                                "teamAllocationAmount" to BigInteger("600000000000000000"), // 0.6
                                "rewardsAllocationAmount" to BigInteger("1000000000000000000"), // 1
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns existingRecord

        val (updated, archived) = service.processEvents(listOf(claimEvent))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(2, u.version)
        assertEquals(3, u.roundId)
        assertEquals("claimApp", u.appId)
        // Voters and votesReceived should remain from existing record
        assertEquals(5, u.voters)
        assertEquals(BigInteger.valueOf(100), u.votesReceived)
        // Allocation amounts should be accumulated
        assertEquals(BigDecimal("2.000000000000000000"), u.totalAmount)
        assertEquals(BigDecimal("0.400000000000000000"), u.unallocatedAmount)
        assertEquals(BigDecimal("0.600000000000000000"), u.teamAllocationAmount)
        assertEquals(BigDecimal("1.000000000000000000"), u.rewardsAllocationAmount)
        assertEquals("block-10", u.blockId)
        assertEquals(10L, u.blockNumber)
        assertEquals(100L, u.blockTimestamp)

        // Existing record should be archived
        assertEquals(1, archived.size)
        assertEquals(existingRecord, archived.first())
    }

    @Test
    fun `processEvents accumulates allocation amounts from multiple ClaimReward events`() {
        val existingRecord =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 3,
                appId = "accumulateApp",
                voters = 0,
                votesReceived = BigInteger.ZERO,
                totalAmount = BigDecimal("1.000000000000000000"), // 1
                unallocatedAmount = BigDecimal("0.200000000000000000"), // 0.2
                teamAllocationAmount = BigDecimal("0.300000000000000000"), // 0.3
                rewardsAllocationAmount = BigDecimal("0.500000000000000000"), // 0.5
            )

        val claimEvent =
            buildIndexedEvent(
                id = "claim-2",
                blockNumber = 10L,
                blockTimestamp = 100L,
                blockId = "block-10",
                eventType = "B3TR_XAllocationRewardsClaimed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 3,
                                "appId" to "accumulateApp",
                                "totalAmount" to BigInteger("1500000000000000000"), // 1.5
                                "unallocatedAmount" to BigInteger("300000000000000000"), // 0.3
                                "teamAllocationAmount" to BigInteger("400000000000000000"), // 0.4
                                "rewardsAllocationAmount" to BigInteger("800000000000000000"), // 0.8
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns existingRecord

        val (updated, archived) = service.processEvents(listOf(claimEvent))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(2, u.version)
        // Amounts should be accumulated (added together)
        assertEquals(BigDecimal("2.500000000000000000"), u.totalAmount)
        assertEquals(BigDecimal("0.500000000000000000"), u.unallocatedAmount)
        assertEquals(BigDecimal("0.700000000000000000"), u.teamAllocationAmount)
        assertEquals(BigDecimal("1.300000000000000000"), u.rewardsAllocationAmount)

        assertEquals(1, archived.size)
        assertEquals(existingRecord, archived.first())
    }

    @Test
    fun `processEvents handles mixed vote and claim events in the same block`() {
        val voteEvent =
            buildIndexedEvent(
                id = "vote-1",
                blockNumber = 15L,
                blockTimestamp = 150L,
                blockId = "block-15",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 4,
                                "appsIds" to listOf("mixApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(50)),
                            )
                    ),
            )

        val claimEvent =
            buildIndexedEvent(
                id = "claim-1",
                blockNumber = 15L,
                blockTimestamp = 150L,
                blockId = "block-15",
                eventType = "B3TR_XAllocationRewardsClaimed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 4,
                                "appId" to "mixApp",
                                "totalAmount" to BigInteger("1500000000000000000"), // 1.5
                                "unallocatedAmount" to BigInteger("250000000000000000"), // 0.25
                                "teamAllocationAmount" to BigInteger("500000000000000000"), // 0.5
                                "rewardsAllocationAmount" to
                                    BigInteger("750000000000000000"), // 0.75
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(voteEvent, claimEvent))

        // Should be two entries: one from vote processing, one updated from claim processing
        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(2, u.version)
        assertEquals(4, u.roundId)
        assertEquals("mixApp", u.appId)
        // Voters and votesReceived from vote event
        assertEquals(1, u.voters)
        assertEquals(BigInteger.valueOf(50), u.votesReceived)
        // Allocation amounts from claim event
        assertEquals(BigDecimal("1.500000000000000000"), u.totalAmount)
        assertEquals(BigDecimal("0.250000000000000000"), u.unallocatedAmount)
        assertEquals(BigDecimal("0.500000000000000000"), u.teamAllocationAmount)
        assertEquals(BigDecimal("0.750000000000000000"), u.rewardsAllocationAmount)
        // Block info should be from final event block
        assertEquals("block-15", u.blockId)
        assertEquals(15L, u.blockNumber)
        assertEquals(150L, u.blockTimestamp)
        // One archived snapshot from vote processing
        assertEquals(1, archived.size)
        assertEquals(1, archived.first().version)
    }

    @Test
    fun `processEvents creates new record from DBAFundsDistributed event`() {
        val dbaEvent =
            buildIndexedEvent(
                id = "dba-1",
                blockNumber = 7L,
                blockTimestamp = 77L,
                blockId = "block-7",
                eventType = "B3TR_DBAFundsDistributed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 5,
                                "appId" to "dbaApp",
                                "amount" to BigInteger("3000000000000000000"), // 3
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(dbaEvent))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(1, u.version)
        assertEquals(5, u.roundId)
        assertEquals("dbaApp", u.appId)
        assertEquals(0, u.voters) // No voters from DBA event
        assertEquals(BigInteger.ZERO, u.votesReceived)
        assertEquals(BigDecimal("3.000000000000000000"), u.totalAmount)
        assertEquals(null, u.unallocatedAmount) // Not set in DBA events
        assertEquals(null, u.teamAllocationAmount) // Not set in DBA events
        assertEquals(null, u.rewardsAllocationAmount) // Not set in DBA events
        assertEquals("block-7", u.blockId)
        assertEquals(7L, u.blockNumber)
        assertEquals(77L, u.blockTimestamp)
        assertEquals(0, archived.size)
    }

    @Test
    fun `processEvents accumulates amounts from multiple DBAFundsDistributed events`() {
        val existingRecord =
            XAllocResult(
                version = 1,
                blockId = "block-0",
                blockNumber = 0L,
                blockTimestamp = 0L,
                roundId = 5,
                appId = "dbaApp",
                voters = 0,
                votesReceived = BigInteger.ZERO,
                totalAmount = BigDecimal("2.000000000000000000"), // 2
            )

        val dbaEvent =
            buildIndexedEvent(
                id = "dba-2",
                blockNumber = 8L,
                blockTimestamp = 88L,
                blockId = "block-8",
                eventType = "B3TR_DBAFundsDistributed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 5,
                                "appId" to "dbaApp",
                                "amount" to BigInteger("1500000000000000000"), // 1.5
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns existingRecord

        val (updated, archived) = service.processEvents(listOf(dbaEvent))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(2, u.version)
        // Total amount should be accumulated (2 + 1.5 = 3.5)
        assertEquals(BigDecimal("3.500000000000000000"), u.totalAmount)
        assertEquals("block-8", u.blockId)
        assertEquals(8L, u.blockNumber)
        assertEquals(88L, u.blockTimestamp)

        // Existing record should be archived
        assertEquals(1, archived.size)
        assertEquals(existingRecord, archived.first())
    }

    @Test
    fun `processEvents handles vote, claim, and DBA events together`() {
        val voteEvent =
            buildIndexedEvent(
                id = "vote-1",
                blockNumber = 20L,
                blockTimestamp = 200L,
                blockId = "block-20",
                eventType = "B3TR_XAllocationVote",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 6,
                                "appsIds" to listOf("multiApp"),
                                "voteWeights" to listOf(BigInteger.valueOf(100)),
                            )
                    ),
            )

        val claimEvent =
            buildIndexedEvent(
                id = "claim-1",
                blockNumber = 21L,
                blockTimestamp = 210L,
                blockId = "block-21",
                eventType = "B3TR_XAllocationRewardsClaimed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 6,
                                "appId" to "multiApp",
                                "totalAmount" to BigInteger("5000000000000000000"), // 5
                                "unallocatedAmount" to BigInteger("1000000000000000000"), // 1
                                "teamAllocationAmount" to BigInteger("1500000000000000000"), // 1.5
                                "rewardsAllocationAmount" to
                                    BigInteger("2500000000000000000"), // 2.5
                            )
                    ),
            )

        val dbaEvent =
            buildIndexedEvent(
                id = "dba-1",
                blockNumber = 22L,
                blockTimestamp = 220L,
                blockId = "block-22",
                eventType = "B3TR_DBAFundsDistributed",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "roundId" to 6,
                                "appId" to "multiApp",
                                "amount" to BigInteger("2000000000000000000"), // 2
                            )
                    ),
            )

        every { repository.findByIdOrNull(any()) } returns null

        val (updated, archived) = service.processEvents(listOf(voteEvent, claimEvent, dbaEvent))

        assertEquals(1, updated.size)
        val u = updated.first()
        assertEquals(3, u.version) // Updated 3 times
        assertEquals(6, u.roundId)
        assertEquals("multiApp", u.appId)
        // From vote event
        assertEquals(1, u.voters)
        assertEquals(BigInteger.valueOf(100), u.votesReceived)
        // From claim and DBA events (claim sets it to 5, DBA adds 2)
        assertEquals(BigDecimal("7.000000000000000000"), u.totalAmount)
        assertEquals(BigDecimal("1.000000000000000000"), u.unallocatedAmount)
        assertEquals(BigDecimal("1.500000000000000000"), u.teamAllocationAmount)
        assertEquals(BigDecimal("2.500000000000000000"), u.rewardsAllocationAmount)
        // Block info from final event
        assertEquals("block-22", u.blockId)
        assertEquals(22L, u.blockNumber)
        assertEquals(220L, u.blockTimestamp)
        // Two archived snapshots
        assertEquals(2, archived.size)
    }
}
