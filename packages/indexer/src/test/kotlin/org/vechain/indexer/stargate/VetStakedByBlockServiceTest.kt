package org.vechain.indexer.stargate.vetStaked

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
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsInt
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
internal class VetStakedByBlockServiceTest {
    @MockK lateinit var repository: VetStakedByBlockRepository

    private lateinit var service: VetStakedByBlockService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = VetStakedByBlockService(repository)
    }

    @Test
    fun `processEvents returns empty when given empty input`() {
        val result = service.processEvents(emptyList())
        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents computes block-level and cumulative totals without previous record`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                mockEvent("b1", 10L, 1000L, "100", TokenLevel.Strength.ordinal),
                mockEvent("b2", 12L, 1200L, "200", TokenLevel.Thunder.ordinal),
            )

        val result = service.processEvents(events)

        expectThat(result).hasSize(2)

        expectThat(result[0]).blockMatches("b1", 10L, 1000L, BigInteger("100"))

        expectThat(result[1]).blockMatches("b2", 12L, 1200L, BigInteger("300"))
    }

    @Test
    fun `processEvents continues totals from previous record`() {
        val latest =
            VetStakedByBlock(
                blockId = "prev",
                blockNumber = 9,
                blockTimestamp = 900,
                total = BigInteger("500"),
                byLevel = mapOf(TokenLevel.Strength to BigInteger("500")),
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 1,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        every { repository.getLatestRecord() } returns latest

        val events = listOf(mockEvent("b10", 10L, 1000L, "50", TokenLevel.Strength.ordinal))

        val result = service.processEvents(events)

        expectThat(result).hasSize(2)
        expectThat(result[1]).blockMatches("b10", 10L, 1000L, BigInteger("550"))
    }

    @Test
    fun `processEvents throws when blockNumber is same or earlier than latest`() {
        val latest =
            VetStakedByBlock(
                blockId = "prev",
                blockNumber = 10L,
                blockTimestamp = 900L,
                total = BigInteger("100"),
                byLevel = emptyMap(),
                dayOfMonth = 1,
                weekOfYear = 1,
                month = 1,
                year = 1,
                timeFrames = emptyList(),
                blockTotal = BigInteger.ZERO,
                dayTotal = BigInteger.ZERO,
                weekTotal = BigInteger.ZERO,
                monthTotal = BigInteger.ZERO,
                yearTotal = BigInteger.ZERO,
            )

        every { repository.getLatestRecord() } returns latest

        val events = listOf(mockEvent("bad", 10L, 1100L, "50", TokenLevel.Strength.ordinal))

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 10")
    }

    @Test
    fun `processEvents throws when missing value`() {
        every { repository.getLatestRecord() } returns null

        val events = listOf(mockEvent("bX", 15L, 1500L, null, TokenLevel.Strength.ordinal))

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }

        expectThat(ex.message)
            .isEqualTo("Event for block 15 (blockId=bX) is missing required 'value'")
    }

    @Test
    fun `processEvents throws when missing levelId`() {
        every { repository.getLatestRecord() } returns null

        val event =
            io.mockk.mockk<IndexedEvent> {
                every { blockId } returns "bX"
                every { blockNumber } returns 20L
                every { blockTimestamp } returns 2000L
                every { eventType } returns "STARGATE_STAKE"
                every { params.getAsInt("levelId") } returns null
                every { params.getAsBigInteger("value") } returns BigInteger("10")
            }

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(listOf(event)) }

        expectThat(ex.message).isEqualTo("Missing levelId in event params")
    }

    @Test
    fun `processEvents throws when invalid levelId`() {
        every { repository.getLatestRecord() } returns null

        val events = listOf(mockEvent("bX", 15L, 1500L, "10", 999))

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }

        expectThat(ex.message).isEqualTo("Invalid levelId: 999")
    }

    @Test
    fun `processEvents throws on unknown eventType`() {
        every { repository.getLatestRecord() } returns null

        val event =
            io.mockk.mockk<IndexedEvent> {
                every { blockId } returns "bX"
                every { blockNumber } returns 22L
                every { blockTimestamp } returns 2200L
                every { eventType } returns "UNKNOWN"
                every { params.getAsInt("levelId") } returns TokenLevel.Strength.ordinal
                every { params.getAsBigInteger("value") } returns BigInteger("10")
            }

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(listOf(event)) }

        expectThat(ex.message).isEqualTo("Unknown eventType: UNKNOWN")
    }

    @Test
    fun `processEvents aggregates multiple events within same block`() {
        every { repository.getLatestRecord() } returns null

        val events =
            listOf(
                mockEvent("b10a", 10L, 1000L, "100", TokenLevel.Strength.ordinal),
                mockEvent("b10b", 10L, 1000L, "50", TokenLevel.Strength.ordinal),
            )

        val result = service.processEvents(events)

        expectThat(result).hasSize(1)
        expectThat(result[0]).blockMatches("b10a", 10L, 1000L, BigInteger("150"))
    }

    @Test
    fun `saveRecords delegates to repository`() {
        val records =
            listOf(
                VetStakedByBlock(
                    blockId = "bX",
                    blockNumber = 5,
                    blockTimestamp = 500,
                    total = BigInteger("10"),
                    byLevel = emptyMap(),
                    dayOfMonth = 1,
                    weekOfYear = 1,
                    month = 1,
                    year = 1,
                    timeFrames = emptyList(),
                    blockTotal = BigInteger.ZERO,
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
        levelId: Int,
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns "STARGATE_STAKE"
            every { params.getAsBigInteger("value") } returns value?.let { BigInteger(it) }
            every { params.getAsInt("levelId") } returns levelId
        }

    private fun Assertion.Builder<VetStakedByBlock>.blockMatches(
        id: String,
        num: Long,
        ts: Long,
        total: BigInteger,
    ) = and {
        get(VetStakedByBlock::blockId).isEqualTo(id)
        get(VetStakedByBlock::blockNumber).isEqualTo(num)
        get(VetStakedByBlock::blockTimestamp).isEqualTo(ts)
        get(VetStakedByBlock::total).isEqualTo(total)
    }
}
