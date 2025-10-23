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
import org.vechain.indexer.stargate.vetStaked.VetStakedByBlockService
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

@ExtendWith(MockKExtension::class)
internal class VetStakedByBlockServiceTest {
    @MockK lateinit var repository: VetStakedByBlockRepository

    private lateinit var service: VetStakedByBlockService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = VetStakedByBlockService(repository)
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
                    value = "100",
                    levelId = 1,
                ),
                mockEvent(
                    blockId = "block2",
                    blockNumber = 12L,
                    blockTimestamp = 1200L,
                    eventType = "STARGATE_STAKE",
                    value = "200",
                    levelId = 2,
                ),
            )
        every { repository.getLatestRecord() } returns null

        val result = service.processEvents(events)

        expectThat(result).hasSize(2)

        expectThat(result[0])
            .andBlockRecord(
                blockId = "block1",
                blockNumber = 10L,
                blockTimestamp = 1000L,
                total = BigInteger("100"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("100")),
            )

        expectThat(result[1])
            .andBlockRecord(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
                byLevel =
                    mapOf(
                        TokenLevel.Strength to BigInteger("100"),
                        TokenLevel.Thunder to BigInteger("200"),
                    ),
            )
    }

    @Test
    fun `processEvents adds to previous totals when previous record exists`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block3",
                    blockNumber = 15L,
                    blockTimestamp = 1500L,
                    eventType = "STARGATE_STAKE",
                    value = "50",
                    levelId = 1,
                )
            )

        val latestRecord =
            VetStakedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
                byLevel =
                    mutableMapOf(
                        TokenLevel.Thunder to BigInteger("200"),
                        TokenLevel.Strength to BigInteger("100"),
                    ),
            )
        every { repository.getLatestRecord() } returns latestRecord

        val result = service.processEvents(events)

        expectThat(result).hasSize(1)
        expectThat(result[0])
            .andBlockRecord(
                blockId = "block3",
                blockNumber = 15L,
                blockTimestamp = 1500L,
                total = BigInteger("350"),
                byLevel =
                    mapOf(
                        TokenLevel.Thunder to BigInteger("200"),
                        TokenLevel.Strength to BigInteger("150"),
                    ),
            )
    }

    @Test
    fun `processEvents fails fast when any incoming blockNumber is same or earlier than last persisted`() {
        val events =
            listOf(
                mockEvent(
                    blockId = "block3",
                    blockNumber = 12L, // same as latest
                    blockTimestamp = 1500L,
                    eventType = "STARGATE_STAKE",
                    value = "50",
                    levelId = 1,
                ),
                mockEvent(
                    blockId = "block4",
                    blockNumber = 13L,
                    blockTimestamp = 1600L,
                    eventType = "STARGATE_STAKE",
                    value = "10",
                    levelId = 2,
                ),
            )

        val latestRecord =
            VetStakedByBlock(
                blockId = "block2",
                blockNumber = 12L,
                blockTimestamp = 1200L,
                total = BigInteger("300"),
                byLevel =
                    mutableMapOf(
                        TokenLevel.Thunder to BigInteger("200"),
                        TokenLevel.Strength to BigInteger("100"),
                    ),
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
        val events =
            listOf(
                mockEvent(
                    blockId = "blockX",
                    blockNumber = 21L,
                    blockTimestamp = 2100L,
                    eventType = "STARGATE_STAKE",
                    value = null, // missing
                    levelId = 1,
                )
            )
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message)
            .isEqualTo("Event for block 21 (blockId=blockX) is missing required 'value' parameter")
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
                    value = "100",
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
                    value = "100",
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
                    eventType = "UNKNOWN_EVENT",
                    value = "10",
                    levelId = 1,
                )
            )
        every { repository.getLatestRecord() } returns null

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Unknown eventType: UNKNOWN_EVENT")
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
                    value = "10",
                    levelId = 1,
                ),
                mockEvent(
                    blockId = "block20b",
                    blockNumber = 20L,
                    blockTimestamp = 2000L,
                    eventType = "STARGATE_STAKE",
                    value = "20",
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
                total = BigInteger("30"),
                byLevel =
                    mapOf(
                        TokenLevel.Strength to BigInteger("10"),
                        TokenLevel.Thunder to BigInteger("20"),
                    ),
            )
    }

    @Test
    fun `saveRecord delegates to repository`() {
        val record =
            VetStakedByBlock(
                blockId = "blockX",
                blockNumber = 99L,
                blockTimestamp = 9999L,
                total = BigInteger("123"),
                byLevel = mutableMapOf(TokenLevel.Dawn to BigInteger("123")),
            )
        every { repository.save(record) } returns record

        service.saveRecord(record)

        verify(exactly = 1) { repository.save(record) }
    }

    @Test
    fun `saveRecords delegates to repository saveAll`() {
        val records =
            listOf(
                VetStakedByBlock(
                    blockId = "blockA",
                    blockNumber = 1L,
                    blockTimestamp = 100L,
                    total = BigInteger("1"),
                    byLevel = mutableMapOf(TokenLevel.Dawn to BigInteger("1")),
                ),
                VetStakedByBlock(
                    blockId = "blockB",
                    blockNumber = 2L,
                    blockTimestamp = 200L,
                    total = BigInteger("2"),
                    byLevel = mutableMapOf(TokenLevel.Thunder to BigInteger("2")),
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
        value: String?,
        levelId: Int?,
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsBigInteger("value") } returns value?.let { BigInteger(it) }
            every { params.getAsInt("levelId") } returns levelId
        }

    private fun Assertion.Builder<VetStakedByBlock>.andBlockRecord(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: BigInteger,
        byLevel: Map<TokenLevel, BigInteger>,
    ) = and {
        get(VetStakedByBlock::blockId).isEqualTo(blockId)
        get(VetStakedByBlock::blockNumber).isEqualTo(blockNumber)
        get(VetStakedByBlock::blockTimestamp).isEqualTo(blockTimestamp)
        get(VetStakedByBlock::total).isEqualTo(total)
        byLevel.forEach { (lvl, amt) ->
            get(VetStakedByBlock::byLevel).and { get { this[lvl] }.isEqualTo(amt) }
        }
    }
}
