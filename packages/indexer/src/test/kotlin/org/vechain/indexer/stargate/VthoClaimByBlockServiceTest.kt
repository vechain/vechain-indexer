package org.vechain.indexer.stargate

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
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlock
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockRepository
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlockService
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class VthoClaimByBlockServiceTest {
    @MockK lateinit var repository: VthoClaimedByBlockRepository

    private lateinit var service: VthoClaimedByBlockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = VthoClaimedByBlockService(repository)
    }

    @Test
    fun `processEvents returns empty list for empty events`() {
        val result = service.processEvents(emptyList())
        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents with no previous record - groups by block and builds cumulative totals`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block1",
                    blockNumber = 10L,
                    blockTimestamp = 1000L,
                    value = "100",
                ),
                mockEvent(
                    blockId = "block2",
                    blockNumber = 12L,
                    blockTimestamp = 1200L,
                    value = "200",
                ),
            )

        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expectThat(result).hasSize(2)

        expectThat(result[0]).andBlockRecord("block1", 10L, 1000L, BigInteger("100"))

        expectThat(result[1]).andBlockRecord("block2", 12L, 1200L, BigInteger("300"))
    }

    @Test
    fun `processEvents adds to previous total when previous record exists`() {
        val latestRecord =
            VthoClaimedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
                legacyRewards = BigInteger.ZERO,
                hourOfDay = 1,
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 1,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                hourTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        every { repository.getLatestRecord() } returns latestRecord

        val events =
            listOf(
                mockEvent(
                    blockId = "block3",
                    blockNumber = 15L,
                    blockTimestamp = 1500L,
                    value = "50",
                )
            )

        val result = service.processEvents(events)

        expectThat(result).hasSize(2)

        expectThat(result[1]).andBlockRecord("block3", 15L, 1500L, BigInteger("350"))
    }

    // ---------------------------------------------------------
    // BLOCK ORDER VALIDATION
    // ---------------------------------------------------------

    @Test
    fun `processEvents fails fast when any incoming blockNumber is same or earlier than last persisted`() {
        val latestRecord =
            VthoClaimedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
                legacyRewards = BigInteger.ZERO,
                hourOfDay = 1,
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 1,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                hourTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        every { repository.getLatestRecord() } returns latestRecord

        val events =
            listOf(
                mockEvent(
                    blockId = "block3",
                    blockNumber = 12L, // SAME -> should fail
                    blockTimestamp = 1500L,
                    value = "50",
                ),
                mockEvent(
                    blockId = "block4",
                    blockNumber = 13L,
                    blockTimestamp = 1600L,
                    value = "10",
                ),
            )

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 12")
    }

    // ---------------------------------------------------------
    // MISSING VALUE PARAM
    // ---------------------------------------------------------

    @Test
    fun `processEvents throws when an event is missing the mandatory value param`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                mockEvent(
                    blockId = "blockX",
                    blockNumber = 21L,
                    blockTimestamp = 2100L,
                    value = null,
                )
            )

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }

        expectThat(ex.message)
            .isEqualTo("Event for block 21 (blockId=blockX) is missing required 'value'")
    }

    // ---------------------------------------------------------
    // MULTIPLE EVENTS — SAME BLOCK
    // ---------------------------------------------------------

    @Test
    fun `processEvents uses the first event as representative within a block`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(mockEvent("block20a", 20L, 2000L, "10"), mockEvent("block20b", 20L, 2000L, "20"))

        val result = service.processEvents(events)

        expectThat(result).hasSize(1)

        expectThat(result[0])
            .andBlockRecord(
                blockId = "block20a", // FIRST event wins
                blockNumber = 20L,
                blockTimestamp = 2000L,
                total = BigInteger("30"),
            )
    }

    // ---------------------------------------------------------
    // SAVE RECORDS
    // ---------------------------------------------------------

    @Test
    fun `saveRecord delegates to repository`() {
        val records =
            listOf(
                VthoClaimedByBlock(
                    blockId = "blockX",
                    blockNumber = 99L,
                    blockTimestamp = 9999L,
                    total = BigInteger("123"),
                    legacyRewards = BigInteger.ZERO,
                    hourOfDay = 1,
                    dayOfMonth = 1,
                    weekOfYear = 1,
                    month = 1,
                    year = 1,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.ZERO,
                    hourTotal = BigInteger.ZERO,
                    dayTotal = BigInteger.ZERO,
                    weekTotal = BigInteger.ZERO,
                    monthTotal = BigInteger.ZERO,
                    yearTotal = BigInteger.ZERO,
                )
            )

        every { repository.saveAll(records) } returns records

        service.saveRecords(records)

        verify(exactly = 1) { repository.saveAll(records) }
    }

    private fun mockEvent(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        value: String?,
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns "STARGATE_CLAIM_REWARDS"
            every { params.getAsBigInteger("value") } returns value?.let { BigInteger(it) }
        }

    private fun Assertion.Builder<VthoClaimedByBlock>.andBlockRecord(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: BigInteger,
    ) = and {
        get(VthoClaimedByBlock::blockId).isEqualTo(blockId)
        get(VthoClaimedByBlock::blockNumber).isEqualTo(blockNumber)
        get(VthoClaimedByBlock::blockTimestamp).isEqualTo(blockTimestamp)
        get(VthoClaimedByBlock::total).isEqualTo(total)
    }
}
