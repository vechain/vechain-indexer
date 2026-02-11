package org.vechain.indexer.stargate

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.archive.ArchiveService
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlock
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockService
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalance
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalanceRepository
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
class NftHolderByBlockServiceTest {
    @MockK lateinit var repository: NftHoldersByBlockRepository
    @MockK lateinit var ownerBalanceRepository: NftOwnerBalanceRepository
    @MockK lateinit var archiveService: ArchiveService<NftOwnerBalance>

    private lateinit var service: NftHoldersByBlockService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = NftHoldersByBlockService(repository, ownerBalanceRepository, archiveService)
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

    private fun nftBlock(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: Long,
        by: Map<TokenLevel, Long> = emptyMap(),
        day: Long = 1,
        week: Long = 1,
        month: Long = 1,
        year: Long = 2025,
        tf: List<TimeFrame> = emptyList(),
        blockTotal: Long = 0,
        dayTotal: Long = 0,
        weekTotal: Long = 0,
        monthTotal: Long = 0,
        yearTotal: Long = 0,
    ): NftHoldersByBlock =
        NftHoldersByBlock(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            total = total,
            byLevel = by,
            hourOfDay = 1L,
            dayOfMonth = day,
            weekOfYear = week,
            month = month,
            year = year,
            timeFrames = tf,
            blockTotal = blockTotal.toBigInteger(),
            hourTotal = blockTotal.toBigInteger(),
            dayTotal = dayTotal.toBigInteger(),
            weekTotal = weekTotal.toBigInteger(),
            monthTotal = monthTotal.toBigInteger(),
            yearTotal = yearTotal.toBigInteger(),
        )

    private fun mockEvent(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        eventType: String,
        levelId: Int?,
        owner: String = "0xdefault",
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsInt("levelId") } returns levelId
            every { params.getAsString("owner") } returns owner
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
        get(NftHoldersByBlock::byLevel).isEqualTo(byLevel)
    }

    // ------------------------------------------------------------
    // TESTS
    // ------------------------------------------------------------

    @Test
    fun `processEvents returns empty for empty input`() {
        val result = service.processEvents(emptyList())
        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents with no previous record - different owners`() {
        val events =
            listOf(
                mockEvent("block1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"),
                mockEvent("block2", 12, 1200, "STARGATE_STAKE", 2, "0xowner2"),
                mockEvent("block3", 13, 1300, "STARGATE_UNSTAKE", 1, "0xowner1"),
            )

        every { repository.getLatestRecord() } returns null
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns emptyList()

        val result = service.processEvents(events)
        expectThat(result).hasSize(3)

        // Owner1 stakes - 1 unique holder
        expectThat(result[0]).andBlockRecord("block1", 10, 1000, 1, mapOf(TokenLevel.Strength to 1))

        // Owner2 stakes - 2 unique holders
        expectThat(result[1])
            .andBlockRecord(
                "block2",
                12,
                1200,
                2,
                mapOf(TokenLevel.Strength to 1, TokenLevel.Thunder to 1),
            )

        // Owner1 unstakes (had only 1 NFT) - back to 1 unique holder
        expectThat(result[2])
            .andBlockRecord(
                "block3",
                13,
                1300,
                1,
                mapOf(TokenLevel.Strength to 0, TokenLevel.Thunder to 1),
            )
    }

    @Test
    fun `processEvents same owner multiple stakes counts as one unique holder`() {
        val events =
            listOf(
                mockEvent("block1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"),
                mockEvent("block2", 12, 1200, "STARGATE_STAKE", 2, "0xowner1"),
                mockEvent("block3", 13, 1300, "STARGATE_STAKE", 1, "0xowner1"),
            )

        every { repository.getLatestRecord() } returns null
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns emptyList()

        val result = service.processEvents(events)
        expectThat(result).hasSize(3)

        // Owner1 stakes first NFT - 1 unique holder
        expectThat(result[0]).andBlockRecord("block1", 10, 1000, 1, mapOf(TokenLevel.Strength to 1))

        // Owner1 stakes second NFT - still 1 unique holder (same owner), but now holder of Thunder
        // level too
        expectThat(result[1])
            .andBlockRecord(
                "block2",
                12,
                1200,
                1,
                mapOf(TokenLevel.Strength to 1, TokenLevel.Thunder to 1),
            )

        // Owner1 stakes third NFT (Strength again) - still 1 unique holder, Strength level holder
        // count unchanged (already a holder)
        expectThat(result[2])
            .andBlockRecord(
                "block3",
                13,
                1300,
                1,
                mapOf(TokenLevel.Strength to 1, TokenLevel.Thunder to 1),
            )
    }

    @Test
    fun `processEvents owner only removed when all NFTs unstaked`() {
        val events =
            listOf(
                mockEvent("block1", 10, 1000, "STARGATE_UNSTAKE", 1, "0xowner1"),
                mockEvent("block2", 12, 1200, "STARGATE_UNSTAKE", 2, "0xowner1"),
            )

        // Owner1 has 2 NFTs before
        val existingBalance =
            NftOwnerBalance(
                owner = "0xowner1",
                total = 2,
                byLevel = mapOf(TokenLevel.Strength to 1, TokenLevel.Thunder to 1),
                blockNumber = 8,
                blockId = "block8",
                blockTimestamp = 800,
                version = 2,
            )

        every { repository.getLatestRecord() } returns
            nftBlock(
                blockId = "block0",
                blockNumber = 9,
                blockTimestamp = 900,
                total = 1,
                by = mapOf(TokenLevel.Strength to 1, TokenLevel.Thunder to 1),
            )
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns listOf(existingBalance)

        val result = service.processEvents(events)

        // First unstake: owner still has 1 NFT, still a unique holder
        expectThat(result[1])
            .andBlockRecord(
                "block1",
                10,
                1000,
                1,
                mapOf(TokenLevel.Strength to 0, TokenLevel.Thunder to 1),
            )

        // Second unstake: owner has 0 NFTs, no longer a unique holder
        expectThat(result[2])
            .andBlockRecord(
                "block2",
                12,
                1200,
                0,
                mapOf(TokenLevel.Strength to 0, TokenLevel.Thunder to 0),
            )
    }

    @Test
    fun `processEvents continues from previous record with new owners`() {
        val latestRecord =
            nftBlock(
                blockId = "block9",
                blockNumber = 9,
                blockTimestamp = 900,
                total = 5,
                by = mapOf(TokenLevel.Strength to 2, TokenLevel.Thunder to 3),
            )

        every { repository.getLatestRecord() } returns latestRecord
        // New owners with no existing balance
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns emptyList()

        val events =
            listOf(
                mockEvent("block10", 10, 1000, "STARGATE_STAKE", 1, "0xnewowner1"),
                mockEvent("block11", 11, 1100, "STARGATE_STAKE", 2, "0xnewowner2"),
            )

        val result = service.processEvents(events)

        expectThat(result[0])
            .andBlockRecord(
                "block9",
                9,
                900,
                5,
                mapOf(TokenLevel.Strength to 2, TokenLevel.Thunder to 3),
            )

        // New unique holder
        expectThat(result[1])
            .andBlockRecord(
                "block10",
                10,
                1000,
                6,
                mapOf(TokenLevel.Strength to 3, TokenLevel.Thunder to 3),
            )

        // Another new unique holder
        expectThat(result[2])
            .andBlockRecord(
                "block11",
                11,
                1100,
                7,
                mapOf(TokenLevel.Strength to 3, TokenLevel.Thunder to 4),
            )
    }

    @Test
    fun `throws on same or earlier block`() {
        val latestRecord =
            nftBlock(
                blockId = "block9",
                blockNumber = 9,
                blockTimestamp = 900,
                total = 5,
                by = mapOf(TokenLevel.Strength to 2),
            )

        every { repository.getLatestRecord() } returns latestRecord

        val events = listOf(mockEvent("x", 9, 950, "STARGATE_STAKE", 1))

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 9")
    }

    @Test
    fun `throws on missing levelId`() {
        every { repository.getLatestRecord() } returns null
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns emptyList()

        val events = listOf(mockEvent("b1", 10, 1000, "STARGATE_STAKE", null))

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Missing levelId in event params")
    }

    @Test
    fun `throws on invalid levelId`() {
        every { repository.getLatestRecord() } returns null
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns emptyList()

        val events = listOf(mockEvent("b1", 10, 1000, "STARGATE_STAKE", 999))

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Invalid levelId: 999")
    }

    @Test
    fun `throws on unknown event type`() {
        every { repository.getLatestRecord() } returns null
        every { ownerBalanceRepository.findByOwnerIn(any()) } returns emptyList()

        val events = listOf(mockEvent("b1", 10, 1000, "WTF", 1))

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Unknown eventType: WTF")
    }

    @Test
    fun `saveRecords delegates and saves owner balances`() {
        val records = listOf(nftBlock("A", 1, 100, 1), nftBlock("B", 2, 200, 2))

        every { repository.saveAll(records) } returns records
        // No owner balances to save after direct saveRecords call
        every { ownerBalanceRepository.saveAll(emptyList<NftOwnerBalance>()) } returns emptyList()

        service.saveRecords(records)

        verify(exactly = 1) { repository.saveAll(records) }
    }
}
