package org.vechain.indexer.stargate.vetDelegated

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
class VetDelegatedByBlockServiceTest {
    @MockK lateinit var repository: VetDelegatedByBlockRepository
    private lateinit var service: VetDelegatedByBlockService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VetDelegatedByBlockService(repository)
    }

    private fun mockEvent(
        blockNumber: Long,
        timestamp: Long,
        amount: String,
        eventType: String = "DelegationInitiated",
        blockId: String = "block-$blockNumber",
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns timestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsBigInteger("amount") } returns BigInteger(amount)
            every { params.getAsInt("levelId") } returns null
        }

    @Test
    fun `empty events returns empty list`() {
        every { repository.getLatestRecord() } returns null
        expectThat(service.processEvents(emptyList())).isEmpty()
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
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 2025,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        val events =
            listOf(
                mockEvent(10, 1100, "1") // EXACT SAME BLOCK → FAIL
            )

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 10")
    }

    // ---------------------------------------------------------
    // DAY ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `DAY rollover - previous block gets DAY tag only`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Dec 30 2025 @ 23:59 UTC (day 30)
                mockEvent(100, 1767043140, "10"),
                // Dec 31 2025 @ 00:00 UTC (day 31)
                mockEvent(101, 1767129600, "5"),
            )

        val r = service.processEvents(events)

        expectThat(r).hasSize(3)

        // r[1] = previous block (block 100) WITH rollover flag
        expectThat(r[1].timeFrames).containsExactly(TimeFrame.DAY)

        // r[2] = block 101 new day → no timeFrames
        expectThat(r[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // WEEK ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `WEEK rollover - previous block gets WEEK tag`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Feb 28 2025 @ 12:00 UTC
                mockEvent(300, 1740744000, "50"),
                // Mar 28 2025 @ 12:00 UTC
                mockEvent(301, 1743336000, "10"),
            )

        val r = service.processEvents(events)

        expectThat(r).hasSize(3)

        expectThat(r[1].timeFrames).contains(TimeFrame.WEEK)
        expectThat(r[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // MONTH ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `MONTH rollover - previous block gets MONTH tag`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Jan 31
                mockEvent(300, 1761961220, "50"),
                // Feb 1
                mockEvent(301, 1761950990, "10"),
            )

        val r = service.processEvents(events)

        expectThat(r).hasSize(3)
        expectThat(r[1].timeFrames).contains(TimeFrame.MONTH)
        expectThat(r[2].timeFrames).isEmpty()
    }

    // ---------------------------------------------------------
    // YEAR ROLLOVER
    // ---------------------------------------------------------

    @Test
    fun `YEAR rollover - previous block gets YEAR tag`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                // Dec 31 2025
                mockEvent(400, 1763487400, "100"),
                // Jan 1 2026
                mockEvent(401, 1707225605, "25"),
            )

        val r = service.processEvents(events)

        expectThat(r).hasSize(3)

        expectThat(r[1].timeFrames).contains(TimeFrame.YEAR)
        expectThat(r[2].timeFrames).isEmpty()
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
                    dayOfMonth = 1,
                    weekOfYear = 1,
                    month = 1,
                    year = 2025,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.ONE,
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
