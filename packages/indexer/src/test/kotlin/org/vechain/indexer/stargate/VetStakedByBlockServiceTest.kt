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
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlock
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockService
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
internal class NftHoldersByBlockServiceTest {
    @MockK lateinit var repository: NftHoldersByBlockRepository

    private lateinit var service: NftHoldersByBlockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NftHoldersByBlockService(repository)
    }

    // ------------------------------------------------------------
    // Helper factory to satisfy NEW required constructor fields
    // ------------------------------------------------------------
    private fun nftBlock(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: Long,
        byLevel: Map<TokenLevel, Long> = emptyMap(),
        dayOfMonth: Long = 1,
        weekOfYear: Long = 1,
        month: Long = 1,
        year: Long = 2025,
        timeFrames: List<org.vechain.indexer.accounts.TimeFrame> = emptyList(),
        blockTotal: BigInteger = BigInteger.ZERO,
        dayTotal: BigInteger = BigInteger.ZERO,
        weekTotal: BigInteger = BigInteger.ZERO,
        monthTotal: BigInteger = BigInteger.ZERO,
        yearTotal: BigInteger = BigInteger.ZERO,
    ): NftHoldersByBlock =
        NftHoldersByBlock(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            total = total,
            byLevel = byLevel,
            dayOfMonth = dayOfMonth,
            weekOfYear = weekOfYear,
            month = month,
            year = year,
            timeFrames = timeFrames,
            blockTotal = blockTotal,
            dayTotal = dayTotal,
            weekTotal = weekTotal,
            monthTotal = monthTotal,
            yearTotal = yearTotal,
        )

    // ------------------------------------------------------------
    // Tests
    // ------------------------------------------------------------

    @Test
    fun `processEvents returns empty list for empty events`() {
        val result = service.processEvents(emptyList())
        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents with no previous record - cumulative per block`() {
        val events =
            listOf(
                mockEvent("block1", 10, 1000, "STARGATE_STAKE", 1),
                mockEvent("block2", 12, 1200, "STARGATE_STAKE", 2),
                mockEvent("block3", 13, 1300, "STARGATE_UNSTAKE", 1),
            )

        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expectThat(result).hasSize(3)

        // Block 10
        expectThat(result[0])
            .andBlockRecord(
                "block1",
                10,
                1000,
                total = 1L,
                byLevel = mapOf(TokenLevel.Strength to 1L),
            )

        // Block 12
        expectThat(result[1])
            .andBlockRecord(
                "block2",
                12,
                1200,
                total = 2L,
                byLevel = mapOf(TokenLevel.Strength to 1L, TokenLevel.Thunder to 1L),
            )

        // Block 13
        expectThat(result[2])
            .andBlockRecord(
                "block3",
                13,
                1300,
                total = 1L,
                byLevel = mapOf(TokenLevel.Strength to 0L, TokenLevel.Thunder to 1L),
            )
    }

    @Test
    fun `processEvents continues from previous record`() {
        val events =
            listOf(
                mockEvent("block10", 10, 1000, "STARGATE_STAKE", 1),
                mockEvent("block11", 11, 1100, "STARGATE_UNSTAKE", 2),
            )

        val latestRecord =
            nftBlock(
                "block9",
                9,
                900,
                total = 5,
                byLevel = mapOf(TokenLevel.Strength to 2, TokenLevel.Thunder to 3),
            )

        every { repository.getLatestRecord() } returns latestRecord

        val result = service.processEvents(events)

        expectThat(result).hasSize(3)

        expectThat(result[0])
            .andBlockRecord(
                "block9",
                9,
                900,
                total = 5,
                byLevel = mapOf(TokenLevel.Strength to 2L, TokenLevel.Thunder to 3L),
            )

        // After block 10
        expectThat(result[1])
            .andBlockRecord(
                "block10",
                10,
                1000,
                total = 6L,
                byLevel = mapOf(TokenLevel.Strength to 3L, TokenLevel.Thunder to 3L),
            )

        // After block 11
        expectThat(result[2])
            .andBlockRecord(
                "block11",
                11,
                1100,
                total = 5L,
                byLevel = mapOf(TokenLevel.Strength to 3L, TokenLevel.Thunder to 2L),
            )
    }

    @Test
    fun `processEvents fails for same or earlier block`() {
        val events = listOf(mockEvent("blockX", 9, 950, "STARGATE_STAKE", 1))

        val latest =
            nftBlock("block9", 9, 900, total = 5, byLevel = mapOf(TokenLevel.Strength to 2))

        every { repository.getLatestRecord() } returns latest

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 9")
    }

    @Test
    fun `processEvents throws on missing levelId`() {
        val events = listOf(mockEvent("block1", 10, 1000, "STARGATE_STAKE", null))
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Missing levelId in event params")
    }

    @Test
    fun `processEvents throws on invalid levelId`() {
        val events = listOf(mockEvent("block1", 10, 1000, "STARGATE_STAKE", 999))
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Invalid levelId: 999")
    }

    @Test
    fun `processEvents throws on unknown eventType`() {
        val events = listOf(mockEvent("blockU", 42, 4200, "UNKNOWN", 1))
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Unknown eventType: UNKNOWN")
    }

    @Test
    fun `processEvents uses first event per block`() {
        val events =
            listOf(
                mockEvent("block20a", 20, 2000, "STARGATE_STAKE", 1),
                mockEvent("block20b", 20, 2000, "STARGATE_UNSTAKE", 2),
            )

        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expectThat(result).hasSize(1)

        expectThat(result[0])
            .andBlockRecord(
                "block20a",
                20,
                2000,
                total = 0L,
                byLevel = mapOf(TokenLevel.Strength to 1L, TokenLevel.Thunder to -1L),
            )
    }

    @Test
    fun `saveRecord delegates to repository`() {
        val record = nftBlock("blockX", 99, 9999, 1)
        every { repository.save(record) } returns record

        service.saveRecord(record)

        verify(exactly = 1) { repository.save(record) }
    }

    @Test
    fun `saveRecords delegates to repository saveAll`() {
        val recA = nftBlock("blockA", 1, 100, 1)
        val recB = nftBlock("blockB", 2, 200, 2)
        val records = listOf(recA, recB)

        every { repository.saveAll(records) } returns records

        service.saveRecords(records)

        verify(exactly = 1) { repository.saveAll(records) }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun mockEvent(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        eventType: String,
        levelId: Int?,
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsInt("levelId") } returns levelId
        }

    private fun Assertion.Builder<NftHoldersByBlock>.andBlockRecord(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: Long,
        byLevel: Map<TokenLevel, Long>,
    ) = and {
        get(NftHoldersByBlock::blockId).isEqualTo(blockId)
        get(NftHoldersByBlock::blockNumber).isEqualTo(blockNumber)
        get(NftHoldersByBlock::blockTimestamp).isEqualTo(blockTimestamp)
        get(NftHoldersByBlock::total).isEqualTo(total)
        byLevel.forEach { (lvl, amt) ->
            get(NftHoldersByBlock::byLevel).and { get { this[lvl] }.isEqualTo(amt) }
        }
    }
}
