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
        // Two events in two different blocks -> two output records, cumulative total
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

        // Record for block 10
        expectThat(result[0])
            .andBlockRecord(
                blockId = "block1",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                total = BigInteger("100"),
            )

        // Record for block 12 (cumulative = 100 + 200)
        expectThat(result[1])
            .andBlockRecord(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
            )
    }

    @Test
    fun `processEvents adds to previous total when previous record exists`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block3",
                    blockNumber = 15L,
                    blockTimestamp = 1500L,
                    value = "50",
                )
            )

        val latestRecord =
            VthoClaimedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val result = service.processEvents(events)

        expectThat(result).hasSize(1)
        expectThat(result[0])
            .andBlockRecord(
                blockId = "block3",
                blockNumber = 15L,
                blockTimestamp = 1500L,
                total = BigInteger("350"), // 300 + 50
            )
    }

    @Test
    fun `processEvents fails fast when any incoming blockNumber is same or earlier than last persisted`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block3",
                    blockNumber = 12L,
                    blockTimestamp = 1500L,
                    value = "50",
                ), // same as latest
                mockEvent(
                    blockId = "block4",
                    blockNumber = 13L,
                    blockTimestamp = 1600L,
                    value = "10",
                ),
            )

        val latestRecord =
            VthoClaimedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message)
            .isEqualTo(
                "Provided events include blockNumber 12 which is <= last persisted blockNumber 12"
            )
    }

    @Test
    fun `processEvents throws when an event is missing the mandatory value param`() {
        // Missing value for the only event -> thrown at the point of access
        val events =
            listOf(
                mockEvent(
                    blockId = "blockX",
                    blockNumber = 21L,
                    blockTimestamp = 2100L,
                    value = null,
                )
            )
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message)
            .isEqualTo("Event for block 21 (blockId=blockX) is missing required 'value' parameter")
    }

    @Test
    fun `processEvents uses the first event as representative within a block`() {
        // Two events in the same block (same timestamp), different ids, sums values, uses first
        // event's id
        val events =
            listOf(
                mockEvent(
                    blockId = "block20a",
                    blockNumber = 20L,
                    blockTimestamp = 2000L,
                    value = "10",
                ), // should be used
                mockEvent(
                    blockId = "block20b",
                    blockNumber = 20L,
                    blockTimestamp = 2000L,
                    value = "20",
                ),
            )
        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expectThat(result).hasSize(1)
        expectThat(result[0])
            .andBlockRecord(
                blockId = "block20a",
                blockNumber = 20L,
                blockTimestamp = 2000L,
                total = BigInteger("30"),
            )
    }

    @Test
    fun `saveRecord delegates to repository`() {
        val records =
            listOf(
                VthoClaimedByBlock(
                    blockId = "blockX",
                    blockNumber = 99L,
                    blockTimestamp = 9999L,
                    total = BigInteger("123"),
                )
            )
        every { repository.saveAll(records) } returns records

        service.saveRecords(records)

        verify(exactly = 1) { repository.saveAll(records) }
    }

    // --- helpers ---

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
            every { params.getAsBigInteger("value") } returns value?.let { BigInteger(it) }
        }

    // Small Strikt extension to assert a VthoClaimedByBlock in one place
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
