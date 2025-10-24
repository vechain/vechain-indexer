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
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlock
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockRepository
import org.vechain.indexer.stargate.nftHolders.NftHoldersByBlockService
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class NftHoldersByBlockServiceTest {
    @MockK lateinit var repository: NftHoldersByBlockRepository

    private lateinit var service: NftHoldersByBlockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NftHoldersByBlockService(repository)
    }

    @Test
    fun `processEvents returns empty list for empty events`() {
        val result = service.processEvents(emptyList())
        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents with no previous record - per-block cumulative totals and byLevel`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block1",
                    blockNumber = 10L,
                    blockTimestamp = 1000L,
                    eventType = "STARGATE_STAKE",
                    levelId = 1,
                ),
                mockEvent(
                    blockId = "block2",
                    blockNumber = 12L,
                    blockTimestamp = 1200L,
                    eventType = "STARGATE_STAKE",
                    levelId = 2,
                ),
                mockEvent(
                    blockId = "block3",
                    blockNumber = 13L,
                    blockTimestamp = 1300L,
                    eventType = "STARGATE_UNSTAKE",
                    levelId = 1,
                ),
            )
        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expectThat(result).hasSize(3)

        // Block 10 snapshot
        expectThat(result[0])
            .andBlockRecord(
                blockId = "block1",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                total = 1L,
                byLevel = mapOf(TokenLevel.Strength to 1L),
            )

        // Block 12 snapshot
        expectThat(result[1])
            .andBlockRecord(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = 2L,
                byLevel = mapOf(TokenLevel.Strength to 1L, TokenLevel.Thunder to 1L),
            )

        // Block 13 snapshot
        expectThat(result[2])
            .andBlockRecord(
                blockId = "block3",
                blockNumber = 13L,
                blockTimestamp = 1300L,
                total = 1L,
                byLevel =
                    mapOf(
                        TokenLevel.Strength to 0L, // 1 stake - 1 unstake
                        TokenLevel.Thunder to 1L,
                    ),
            )
    }

    @Test
    fun `processEvents continues from previous record`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block10",
                    blockNumber = 10L,
                    blockTimestamp = 1000L,
                    eventType = "STARGATE_STAKE",
                    levelId = 1,
                ),
                mockEvent(
                    blockId = "block11",
                    blockNumber = 11L,
                    blockTimestamp = 1100L,
                    eventType = "STARGATE_UNSTAKE",
                    levelId = 2,
                ),
            )

        val latestRecord =
            NftHoldersByBlock(
                blockId = "block9",
                blockNumber = 9L,
                blockTimestamp = 900L,
                total = 5L,
                byLevel = mutableMapOf(TokenLevel.Strength to 2L, TokenLevel.Thunder to 3L),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val result = service.processEvents(events)

        expectThat(result).hasSize(2)

        // After block 10: +1 Strength
        expectThat(result[0])
            .andBlockRecord(
                blockId = "block10",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                total = 6L,
                byLevel =
                    mapOf(
                        TokenLevel.Strength to 3L, // 2 + 1
                        TokenLevel.Thunder to 3L,
                    ),
            )

        // After block 11: -1 Thunder
        expectThat(result[1])
            .andBlockRecord(
                blockId = "block11",
                blockNumber = 11L,
                blockTimestamp = 1100L,
                total = 5L,
                byLevel =
                    mapOf(
                        TokenLevel.Strength to 3L,
                        TokenLevel.Thunder to 2L, // 3 - 1
                    ),
            )
    }

    @Test
    fun `processEvents fails fast on same or earlier block than last persisted`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "blockX",
                    blockNumber = 9L, // same as latest
                    blockTimestamp = 950L,
                    eventType = "STARGATE_STAKE",
                    levelId = 1,
                )
            )

        val latestRecord =
            NftHoldersByBlock(
                blockId = "block9",
                blockNumber = 9L,
                blockTimestamp = 900L,
                total = 5L,
                byLevel = mutableMapOf(TokenLevel.Strength to 2L),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message)
            .isEqualTo(
                "Provided events include blockNumber 9 which is <= last persisted blockNumber 9"
            )
    }

    @Test
    fun `processEvents throws when levelId is missing`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block1",
                    blockNumber = 10L,
                    blockTimestamp = 1000L,
                    eventType = "STARGATE_STAKE",
                    levelId = null, // missing
                )
            )
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Missing levelId in event params")
    }

    @Test
    fun `processEvents throws when levelId is invalid`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block1",
                    blockNumber = 10L,
                    blockTimestamp = 1000L,
                    eventType = "STARGATE_STAKE",
                    levelId = 999, // invalid
                )
            )
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Invalid levelId: 999")
    }

    @Test
    fun `processEvents throws on unknown eventType`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "blockU",
                    blockNumber = 42L,
                    blockTimestamp = 4200L,
                    eventType = "UNKNOWN",
                    levelId = 1,
                )
            )
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Unknown eventType: UNKNOWN")
    }

    @Test
    fun `processEvents uses the first event as representative within a block`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block20a", // should be used
                    blockNumber = 20L,
                    blockTimestamp = 2000L,
                    eventType = "STARGATE_STAKE",
                    levelId = 1,
                ),
                mockEvent(
                    blockId = "block20b",
                    blockNumber = 20L,
                    blockTimestamp = 2000L,
                    eventType = "STARGATE_UNSTAKE",
                    levelId = 2,
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
                total = 0L, // +1 then -1
                byLevel = mapOf(TokenLevel.Strength to 1L, TokenLevel.Thunder to -1L),
            )
    }

    @Test
    fun `saveRecord delegates to repository`() {
        val record =
            NftHoldersByBlock(
                blockId = "blockX",
                blockNumber = 99L,
                blockTimestamp = 9999L,
                total = 1L,
                byLevel = mutableMapOf(TokenLevel.Dawn to 1L),
            )
        every { repository.save(record) } returns record

        service.saveRecord(record)

        verify(exactly = 1) { repository.save(record) }
    }

    @Test
    fun `saveRecords delegates to repository saveAll`() {
        val records =
            listOf(
                NftHoldersByBlock(
                    blockId = "blockA",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    total = 1L,
                    byLevel = mutableMapOf(TokenLevel.Dawn to 1L),
                ),
                NftHoldersByBlock(
                    blockId = "blockB",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    total = 2L,
                    byLevel = mutableMapOf(TokenLevel.Thunder to 2L),
                ),
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
