package org.vechain.indexer.stargate.vetDelegated

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Block
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.DelegationWriteRepository
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
class VetDelegatedByBlockServiceTest {
    @MockK lateinit var repository: VetDelegatedWriteRepository
    @MockK lateinit var delegationRepository: DelegationWriteRepository
    private lateinit var service: VetDelegatedByBlockService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VetDelegatedByBlockService(repository, delegationRepository)
    }

    private fun mockBlock(
        blockNumber: Long,
        timestamp: Long,
        blockId: String = "block-$blockNumber",
        parentID: String = "block-${blockNumber - 1}",
    ): Block = mockk {
        every { number } returns blockNumber
        every { this@mockk.timestamp } returns timestamp
        every { id } returns blockId
        every { this@mockk.parentID } returns parentID
    }

    private fun period(
        hour: Long,
        day: Long,
        week: Long,
        month: Long,
        year: Long,
        total: BigInteger,
    ) =
        TimeFramePeriod(
            hour,
            day,
            week,
            month,
            year,
            emptyList(),
            total,
            total,
            total,
            total,
            total,
            total,
        )

    private fun delegation(
        id: String,
        level: TokenLevel,
        amount: String,
        status: DelegationStatus = DelegationStatus.ACTIVE,
    ) =
        Delegation(
            id = id,
            validator = "0xvalidator",
            tokenId = id,
            owner = "0xowner",
            status = status,
            tokenLevel = level,
            stakedAmount = amount,
            totalRewardsClaimed = BigInteger.ZERO,
            txId = "0xtx",
            blockId = "block-1",
            blockNumber = 1,
            blockTimestamp = 1,
        )

    private fun mockActiveAggregation(vararg levels: Pair<TokenLevel, String>) {
        every { delegationRepository.findActive() } returns
            levels.mapIndexed { i, (level, amount) -> delegation("$i", level, amount) }
    }

    @Test
    fun `duplicate block throws`() {
        every { repository.latest() } returns
            VetDelegatedByBlock(
                "block-10",
                10,
                1000,
                total = BigInteger("100"),
                byLevel = emptyMap(),
                period = period(1, 1, 1, 1, 2025, BigInteger.ZERO),
            )

        val block = mockBlock(10, 1100) // EXACT SAME BLOCK → FAIL

        val ex = assertThrows<IllegalStateException> { service.processBlock(block, emptyList()) }
        expectThat(ex.message).isEqualTo("Block 10 is at or before last persisted block 10")
    }

    @Test
    fun `backward block throws`() {
        every { repository.latest() } returns
            VetDelegatedByBlock(
                "block-10",
                10,
                1000,
                total = BigInteger("100"),
                byLevel = emptyMap(),
                period = period(1, 1, 1, 1, 2025, BigInteger.ZERO),
            )

        val block = mockBlock(5, 900) // EARLIER BLOCK → FAIL

        val ex = assertThrows<IllegalStateException> { service.processBlock(block, emptyList()) }
        expectThat(ex.message).isEqualTo("Block 5 is at or before last persisted block 10")
    }

    @Test
    fun `forward gap logs warning but does not throw`() {
        every { repository.latest() } returns
            VetDelegatedByBlock(
                "block-10",
                10,
                1735560000, // Dec 30 2024 @ 12:00 UTC
                total = BigInteger("100"),
                byLevel = emptyMap(),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        mockActiveAggregation(TokenLevel.Strength to "100")

        // Block 15 with a gap of 5, same hour → should NOT throw
        val block = mockBlock(15, 1735560050)
        val result = service.processBlock(block, emptyList())

        // No change in total, no rollover → empty result, but no exception
        expectThat(result).isEmpty()
    }

    @Test
    fun `aggregates active delegations correctly`() {
        every { repository.latest() } returns null
        mockActiveAggregation(
            TokenLevel.Strength to "1000000000000000000",
            TokenLevel.Thunder to "5000000000000000000",
        )

        val block = mockBlock(100, 1767043140)
        val result = service.processBlock(block, emptyList())

        expectThat(result).hasSize(1)
        expectThat(result[0].total).isEqualTo(BigInteger("6000000000000000000"))
        expectThat(result[0].byLevel).hasSize(2)
        expectThat(result[0].byLevel[TokenLevel.Strength])
            .isEqualTo(BigInteger("1000000000000000000"))
    }

    // ---------------------------------------------------------
    // DAY/HOUR ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `DAY rollover - previous block gets DAY tag`() {
        // Previous record from Dec 30 2025 @ 23:59 UTC
        every { repository.latest() } returns
            VetDelegatedByBlock(
                "block-100",
                100,
                1735603140, // Dec 30 2025 @ 23:59 UTC
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(23, 30, 1, 12, 2025, BigInteger.ZERO),
            )
        mockActiveAggregation(TokenLevel.Strength to "10")

        // New block on Dec 31 2025 @ 00:00 UTC (day 31)
        val block = mockBlock(101, 1735689600)
        val r = service.processBlock(block, emptyList())

        expectThat(r).hasSize(2)

        // r[0] = previous block WITH rollover flag
        expectThat(r[0].timeFrames).contains(TimeFrame.DAY)
        expectThat(r[0].timeFrames).contains(TimeFrame.HOUR)

        // r[1] = new block → no timeFrames
        expectThat(r[1].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // SAVE RECORDS
    // ---------------------------------------------------------

    @Test
    fun `skips save when no change and no rollover`() {
        // Previous record exists with total = 10 @ Dec 30 2024 12:00 UTC
        every { repository.latest() } returns
            VetDelegatedByBlock(
                "block-100",
                100,
                1735560000, // Dec 30 2024 @ 12:00 UTC
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        // Same total as before - no change
        mockActiveAggregation(TokenLevel.Strength to "10")

        // New block in same hour (10 seconds later, still 12:00)
        val block = mockBlock(101, 1735560010)
        val result = service.processBlock(block, emptyList())

        // Should return empty list - no doc to save
        expectThat(result).isEmpty()
    }

    @Test
    fun `saves when there is a change even without rollover`() {
        // Previous record exists with total = 10 @ Dec 30 2024 12:00 UTC
        every { repository.latest() } returns
            VetDelegatedByBlock(
                "block-100",
                100,
                1735560000, // Dec 30 2024 @ 12:00 UTC
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        // Different total - there IS a change
        mockActiveAggregation(TokenLevel.Strength to "20")

        // New block in same hour (10 seconds later, still 12:00)
        val block = mockBlock(101, 1735560010)
        val result = service.processBlock(block, emptyList())

        // Should return 1 doc because there's a change
        expectThat(result).hasSize(1)
        expectThat(result[0].total).isEqualTo(BigInteger("20"))
    }

    // ---------------------------------------------------------
    // CACHE BEHAVIOUR
    // ---------------------------------------------------------

    @Test
    fun `cache is advanced on skipped blocks and used for next block`() {
        // Block 100 is the latest in DB
        val latestRecord =
            VetDelegatedByBlock(
                "block-100",
                100,
                1735560000,
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        every { repository.latest() } returns latestRecord

        // Block 101: no change → skipped, cache advanced
        mockActiveAggregation(TokenLevel.Strength to "10")
        val block101 = mockBlock(101, 1735560010)
        val result101 = service.processBlock(block101, emptyList())
        expectThat(result101).isEmpty()

        // Block 102: no change → skipped, should use cache (not DB)
        val block102 = mockBlock(102, 1735560020)
        val result102 = service.processBlock(block102, emptyList())
        expectThat(result102).isEmpty()

        // DB should only have been called once (for block 101)
        verify(exactly = 1) { repository.latest() }
    }

    @Test
    fun `cache is advanced across multiple skipped blocks then used for saved block`() {
        val latestRecord =
            VetDelegatedByBlock(
                "block-100",
                100,
                1735560000,
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        every { repository.latest() } returns latestRecord

        // Blocks 101-103: no change → skipped
        mockActiveAggregation(TokenLevel.Strength to "10")
        service.processBlock(mockBlock(101, 1735560010), emptyList())
        service.processBlock(mockBlock(102, 1735560020), emptyList())
        service.processBlock(mockBlock(103, 1735560030), emptyList())

        // Block 104: delegation changes → should produce a record using cached state
        val result =
            service.processBlock(
                mockBlock(104, 1735560040),
                listOf(delegation("0", TokenLevel.Strength, "20")),
            )

        expectThat(result).hasSize(1)
        expectThat(result[0].total).isEqualTo(BigInteger("20"))
        expectThat(result[0].blockNumber).isEqualTo(104)

        // DB should only have been called once (for block 101)
        verify(exactly = 1) { repository.latest() }
    }

    @Test
    fun `rollover after skipped blocks emits record whose id matches its blockNumber`() {
        // Reproduces the bug where `id` defaulted at construction-time was never refreshed by
        // `advanceCache`, causing a rollover emitted from the cached `latest` to upsert into the
        // original record's _id and overwrite it.
        val latestRecord =
            VetDelegatedByBlock(
                "block-100",
                100,
                1735560000, // Dec 30 2024 @ 12:00 UTC
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        every { repository.latest() } returns latestRecord
        mockActiveAggregation(TokenLevel.Strength to "10")

        // Five skipped blocks within the same hour → cache advances each time.
        service.processBlock(mockBlock(101, 1735560010), emptyList())
        service.processBlock(mockBlock(102, 1735560020), emptyList())
        service.processBlock(mockBlock(103, 1735560030), emptyList())
        service.processBlock(mockBlock(104, 1735560040), emptyList())
        service.processBlock(mockBlock(105, 1735560050), emptyList())

        // Block 106 crosses into hour 13 → HOUR rollover.
        val result = service.processBlock(mockBlock(106, 1735563600), emptyList())

        expectThat(result).hasSize(2)
        expectThat(result[0].timeFrames).contains(TimeFrame.HOUR)
        expectThat(result[0].blockNumber).isEqualTo(105)
        expectThat(result[1].blockNumber).isEqualTo(106)
    }

    @Test
    fun `cache falls back to DB when parentID does not match`() {
        val latestRecord =
            VetDelegatedByBlock(
                "block-100",
                100,
                1735560000,
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                period = period(12, 30, 53, 12, 2024, BigInteger.ZERO),
            )
        every { repository.latest() } returns latestRecord
        mockActiveAggregation(TokenLevel.Strength to "10")

        // Block 101 with mismatched parentID
        val block = mockBlock(101, 1735560010, parentID = "wrong-parent-id")
        service.processBlock(block, emptyList())

        // Should have fallen back to DB
        verify(exactly = 1) { repository.latest() }
    }

    // ---------------------------------------------------------
    // SAVE
    // ---------------------------------------------------------

    @Test
    fun `save delegates to repository`() {
        val dummy =
            listOf(
                VetDelegatedByBlock(
                    "b",
                    1,
                    1,
                    total = BigInteger.ONE,
                    byLevel = emptyMap(),
                    period = period(1, 1, 1, 1, 2025, BigInteger.ONE),
                )
            )

        every { repository.save(dummy) } returns Unit

        service.save(dummy, emptyList())

        verify(exactly = 1) { repository.save(dummy) }
    }

    @Test
    fun `the block's changes override the committed set and save applies them`() {
        every { repository.latest() } returns null
        every { repository.save(any()) } returns Unit
        mockActiveAggregation(TokenLevel.Strength to "10", TokenLevel.Thunder to "5")
        val exited = listOf(delegation("0", TokenLevel.Strength, "10", DelegationStatus.EXITED))

        val first = service.processBlock(mockBlock(101, 1735560010), exited)
        service.save(first, exited)
        val second = service.processBlock(mockBlock(102, 1735560020), emptyList())

        expectThat(first.single().total).isEqualTo(BigInteger("5"))
        expectThat(first.single().nftCountByLevel).isEqualTo(mapOf(TokenLevel.Thunder to 1L))
        expectThat(second).isEmpty()
        verify(exactly = 1) { delegationRepository.findActive() }

        service.resetCache()
        service.processBlock(mockBlock(103, 1735560030), emptyList())
        verify(exactly = 2) { delegationRepository.findActive() }
    }

    @Test
    fun `a delegation the committed set lacks joins it through the block's changes`() {
        every { repository.latest() } returns null
        every { repository.save(any()) } returns Unit
        mockActiveAggregation(TokenLevel.Strength to "10")
        val activated = listOf(delegation("7", TokenLevel.Thunder, "5"))
        val exited = listOf(delegation("7", TokenLevel.Thunder, "5", DelegationStatus.EXITED))

        val first = service.processBlock(mockBlock(101, 1735560010), activated)
        service.save(first, activated)
        val second = service.processBlock(mockBlock(102, 1735560020), exited)

        expectThat(first.single().total).isEqualTo(BigInteger("15"))
        expectThat(first.single().nftCountByLevel)
            .isEqualTo(mapOf(TokenLevel.Strength to 1L, TokenLevel.Thunder to 1L))
        expectThat(second.single().total).isEqualTo(BigInteger("10"))
    }
}
