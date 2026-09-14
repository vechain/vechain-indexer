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
import org.vechain.indexer.accounts.TimeFrame
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByBlock
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedByToken
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedService
import org.vechain.indexer.stargate.vthoClaimed.VthoClaimedWriteRepository
import org.vechain.indexer.timeseries.TimeFramePeriod
import org.vechain.indexer.utils.ParamUtils.getAsBigInteger
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

@ExtendWith(MockKExtension::class)
internal class VthoClaimedServiceTest {
    @MockK lateinit var repository: VthoClaimedWriteRepository

    private lateinit var service: VthoClaimedService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        every { repository.latest() } returns null
        every { repository.findCurrentByAccounts(any()) } returns emptyList()
        service = VthoClaimedService(repository)
    }

    @Test
    fun `processEvents returns an empty update for no events`() {
        expectThat(service.processEvents(emptyList()).isEmpty()).isTrue()
    }

    @Test
    fun `groups by block and builds cumulative totals with no previous record`() {
        val events =
            listOf(mockEvent("block1", 10L, 1000L, "100"), mockEvent("block2", 12L, 1200L, "200"))

        val result = service.processEvents(events).byBlock

        expectThat(result).hasSize(2)
        expectThat(result[0]).andBlockRecord("block1", 10L, 1000L, BigInteger("100"))
        expectThat(result[1]).andBlockRecord("block2", 12L, 1200L, BigInteger("300"))
    }

    @Test
    fun `adds to the previous record and splits legacy from delegation claims`() {
        every { repository.latest() } returns
            record("block2", 12L, 1200L, total = BigInteger("300"), legacy = BigInteger("5"))

        val events =
            listOf(
                mockEvent("block3", 15L, 1500L, "50"),
                mockEvent(
                    "block3",
                    15L,
                    1500L,
                    "7",
                    eventType = "STARGATE_CLAIM_REWARDS_BASE_LEGACY",
                ),
            )

        val result = service.processEvents(events).byBlock

        expectThat(result).hasSize(2)
        expectThat(result[1]).andBlockRecord("block3", 15L, 1500L, BigInteger("350"))
        expectThat(result[1].legacyRewards).isEqualTo(BigInteger("12"))
        expectThat(result[1].blockTotal).isEqualTo(BigInteger("50"))
    }

    @Test
    fun `an hour rollover re-emits the previous record tagged with the frames that closed`() {
        val noon = service.processEvents(listOf(mockEvent("block2", 12L, 1735560000L, "1")))
        every { repository.latest() } returns noon.byBlock.single()

        val result =
            service.processEvents(listOf(mockEvent("block3", 15L, 1735563600L, "1"))).byBlock

        expectThat(result).hasSize(2)
        expectThat(result[0].blockNumber).isEqualTo(12L)
        expectThat(result[0].timeFrames).containsExactly(TimeFrame.HOUR)
        expectThat(result[1].timeFrames).isEmpty()
    }

    @Test
    fun `each token accumulates its own totals across blocks and existing rows`() {
        every { repository.findCurrentByAccounts(setOf("0xabc", "0xdef")) } returns
            listOf(
                VthoClaimedByToken(
                    "0xdef",
                    "987",
                    BigInteger.ZERO,
                    BigInteger("300"),
                    "b3",
                    3L,
                    300L,
                )
            )
        val events =
            listOf(
                mockEvent("block1", 10L, 1000L, "100", owner = "0xabc", tokenId = "123"),
                mockEvent("block2", 12L, 1200L, "200", owner = "0xabc", tokenId = "123"),
                mockEvent("block2", 12L, 1200L, "200", owner = "0xabc", tokenId = "456"),
                mockEvent(
                    "block3",
                    13L,
                    1300L,
                    "50",
                    owner = "0xdef",
                    tokenId = "987",
                    eventType = "STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY",
                ),
            )

        val rows = service.processEvents(events).byToken

        expectThat(rows.map { Triple(it.account, it.tokenId, it.blockNumber) })
            .containsExactly(
                Triple("0xabc", "123", 10L),
                Triple("0xabc", "123", 12L),
                Triple("0xabc", "456", 12L),
                Triple("0xdef", "987", 13L),
            )
        expectThat(rows[1].delegationRewards).isEqualTo(BigInteger("300"))
        expectThat(rows[1].blockId).isEqualTo("block2")
        expectThat(rows[2].delegationRewards).isEqualTo(BigInteger("200"))
        expectThat(rows[3].legacyRewards).isEqualTo(BigInteger("50"))
        expectThat(rows[3].delegationRewards).isEqualTo(BigInteger("300"))
    }

    @Test
    fun `fails fast when any incoming block is at or before the last persisted one`() {
        every { repository.latest() } returns
            record("block2", 12L, 1200L, total = BigInteger("300"))

        val events =
            listOf(mockEvent("block3", 12L, 1500L, "50"), mockEvent("block4", 13L, 1600L, "10"))

        val ex = assertThrows<IllegalStateException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Events include block ≤ last persisted block 12")
    }

    @Test
    fun `throws when an event is missing the value or the token id`() {
        val noValue =
            assertThrows<IllegalStateException> {
                service.processEvents(listOf(mockEvent("blockX", 21L, 2100L, null)))
            }
        expectThat(noValue.message)
            .isEqualTo("Event for block 21 (blockId=blockX) is missing required 'value'")

        val noToken =
            assertThrows<IllegalArgumentException> {
                service.processEvents(listOf(mockEvent("blockX", 21L, 2100L, "1", tokenId = null)))
            }
        expectThat(noToken.message).isEqualTo("Missing 'tokenId' parameter in event")
    }

    @Test
    fun `uses the first event as representative within a block`() {
        val events =
            listOf(mockEvent("block20a", 20L, 2000L, "10"), mockEvent("block20b", 20L, 2000L, "20"))

        val result = service.processEvents(events).byBlock

        expectThat(result).hasSize(1)
        expectThat(result[0]).andBlockRecord("block20a", 20L, 2000L, BigInteger("30"))
    }

    @Test
    fun `save writes both tables through the repository`() {
        val update =
            VthoClaimedService.Update(
                listOf(record("blockX", 99L, 9999L, total = BigInteger("123"))),
                listOf(
                    VthoClaimedByToken(
                        "0xabc",
                        "1",
                        BigInteger.ZERO,
                        BigInteger.ONE,
                        "blockX",
                        99L,
                        9999L,
                    )
                ),
            )
        every { repository.save(update.byBlock, update.byToken) } returns Unit

        service.save(update)

        verify(exactly = 1) { repository.save(update.byBlock, update.byToken) }
    }

    private fun record(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        total: BigInteger,
        legacy: BigInteger = BigInteger.ZERO,
    ) =
        VthoClaimedByBlock(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            total = total,
            legacyRewards = legacy,
            period = TimeFramePeriod(1, 1, 1, 1, 1),
        )

    private fun mockEvent(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        value: String?,
        owner: String = "0xabc",
        tokenId: String? = "123",
        eventType: String = "STARGATE_CLAIM_REWARDS",
    ): IndexedEvent =
        io.mockk.mockk {
            every { this@mockk.blockId } returns blockId
            every { this@mockk.blockNumber } returns blockNumber
            every { this@mockk.blockTimestamp } returns blockTimestamp
            every { this@mockk.eventType } returns eventType
            every { params.getAsBigInteger("value") } returns value?.let { BigInteger(it) }
            every { params.getAsString("owner") } returns owner
            every { params.getAsString("tokenId") } returns tokenId
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
