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
import org.vechain.indexer.validator.DelegationLevelAggregateResult
import org.vechain.indexer.validator.DelegationRepository
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
class VetDelegatedByBlockServiceTest {
    @MockK lateinit var repository: VetDelegatedByBlockRepository
    @MockK lateinit var delegationRepository: DelegationRepository
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
    ): Block = mockk {
        every { number } returns blockNumber
        every { this@mockk.timestamp } returns timestamp
        every { id } returns blockId
    }

    private fun mockActiveAggregation(vararg levels: Pair<TokenLevel, String>) {
        every { delegationRepository.aggregateActiveDelegationsByLevel() } returns
            levels.map { (level, amount) -> DelegationLevelAggregateResult(level.name, amount, 1) }
    }

    @Test
    fun `block order violation throws`() {
        every { repository.getLatestRecord() } returns
            VetDelegatedByBlock(
                "block-10",
                10,
                1000,
                total = BigInteger("100"),
                byLevel = emptyMap(),
                hourOfDay = 1,
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 2025,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                hourTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        val block = mockBlock(10, 1100) // EXACT SAME BLOCK → FAIL

        val ex = assertThrows<IllegalStateException> { service.processBlock(block) }
        expectThat(ex.message).isEqualTo("Block 10 ≤ last persisted block 10")
    }

    @Test
    fun `aggregates active delegations correctly`() {
        every { repository.getLatestRecord() } returns null
        mockActiveAggregation(
            TokenLevel.Strength to "1000000000000000000",
            TokenLevel.Thunder to "5000000000000000000",
        )

        val block = mockBlock(100, 1767043140)
        val result = service.processBlock(block)

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
        every { repository.getLatestRecord() } returns
            VetDelegatedByBlock(
                "block-100",
                100,
                1735603140, // Dec 30 2025 @ 23:59 UTC
                total = BigInteger("10"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("10")),
                hourOfDay = 23,
                dayOfMonth = 30,
                weekOfYear = 1,
                month = 12,
                year = 2025,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                hourTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )
        mockActiveAggregation(TokenLevel.Strength to "10")

        // New block on Dec 31 2025 @ 00:00 UTC (day 31)
        val block = mockBlock(101, 1735689600)
        val r = service.processBlock(block)

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
    fun `save delegates to repository`() {
        val dummy =
            listOf(
                VetDelegatedByBlock(
                    "b",
                    1,
                    1,
                    total = BigInteger.ONE,
                    byLevel = emptyMap(),
                    hourOfDay = 1,
                    dayOfMonth = 1,
                    weekOfYear = 1,
                    month = 1,
                    year = 2025,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.ONE,
                    hourTotal = BigInteger.ONE,
                    dayTotal = BigInteger.ONE,
                    weekTotal = BigInteger.ONE,
                    monthTotal = BigInteger.ONE,
                    yearTotal = BigInteger.ONE,
                )
            )

        every { repository.saveAll(dummy) } returns dummy

        service.saveRecords(dummy)

        verify(exactly = 1) { repository.saveAll(dummy) }
    }
}
