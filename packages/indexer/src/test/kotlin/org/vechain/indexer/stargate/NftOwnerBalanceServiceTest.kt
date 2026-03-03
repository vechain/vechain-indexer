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
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalance
import org.vechain.indexer.stargate.nftHolders.NftOwnerBalanceRepository
import org.vechain.indexer.stargate.nftOwnerBalance.NftOwnerBalanceService
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString
import strikt.api.expectThat
import strikt.assertions.*

@ExtendWith(MockKExtension::class)
class NftOwnerBalanceServiceTest {
    @MockK lateinit var repository: NftOwnerBalanceRepository

    private lateinit var service: NftOwnerBalanceService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this)
        service = NftOwnerBalanceService(repository)
    }

    // ------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------

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

    // ------------------------------------------------------------
    // TESTS
    // ------------------------------------------------------------

    @Test
    fun `processEvents returns empty for empty input`() {
        val result = service.processEvents(emptyList())
        expectThat(result).isEmpty()
    }

    @Test
    fun `single STAKE creates one balance doc`() {
        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns emptyList()

        val events = listOf(mockEvent("b1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"))

        val result = service.processEvents(events)
        expectThat(result).hasSize(1)
        expectThat(result[0]) {
            get(NftOwnerBalance::owner).isEqualTo("0xowner1")
            get(NftOwnerBalance::total).isEqualTo(1L)
            get(NftOwnerBalance::byLevel).isEqualTo(mapOf(TokenLevel.Strength to 1L))
            get(NftOwnerBalance::blockNumber).isEqualTo(10L)
            get(NftOwnerBalance::id).isEqualTo("0xowner1_10")
        }
    }

    @Test
    fun `multiple events same owner same block produces one doc with net result`() {
        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns emptyList()

        val events =
            listOf(
                mockEvent("b1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"),
                mockEvent("b1", 10, 1000, "STARGATE_STAKE", 2, "0xowner1"),
                mockEvent("b1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"),
            )

        val result = service.processEvents(events)
        expectThat(result).hasSize(1)
        expectThat(result[0]) {
            get(NftOwnerBalance::owner).isEqualTo("0xowner1")
            get(NftOwnerBalance::total).isEqualTo(3L)
            get(NftOwnerBalance::byLevel)
                .isEqualTo(mapOf(TokenLevel.Strength to 2L, TokenLevel.Thunder to 1L))
        }
    }

    @Test
    fun `multiple blocks produce correct per-block docs`() {
        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns emptyList()

        val events =
            listOf(
                mockEvent("b1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"),
                mockEvent("b2", 12, 1200, "STARGATE_STAKE", 2, "0xowner1"),
            )

        val result = service.processEvents(events)
        expectThat(result).hasSize(2)

        expectThat(result[0]) {
            get(NftOwnerBalance::blockNumber).isEqualTo(10L)
            get(NftOwnerBalance::total).isEqualTo(1L)
            get(NftOwnerBalance::byLevel).isEqualTo(mapOf(TokenLevel.Strength to 1L))
        }

        expectThat(result[1]) {
            get(NftOwnerBalance::blockNumber).isEqualTo(12L)
            get(NftOwnerBalance::total).isEqualTo(2L)
            get(NftOwnerBalance::byLevel)
                .isEqualTo(mapOf(TokenLevel.Strength to 1L, TokenLevel.Thunder to 1L))
        }
    }

    @Test
    fun `multiple owners in same block produce separate docs`() {
        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns emptyList()

        val events =
            listOf(
                mockEvent("b1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"),
                mockEvent("b1", 10, 1000, "STARGATE_STAKE", 2, "0xowner2"),
            )

        val result = service.processEvents(events)
        expectThat(result).hasSize(2)

        val byOwner = result.associateBy { it.owner }
        expectThat(byOwner["0xowner1"]!!) {
            get(NftOwnerBalance::total).isEqualTo(1L)
            get(NftOwnerBalance::byLevel).isEqualTo(mapOf(TokenLevel.Strength to 1L))
        }
        expectThat(byOwner["0xowner2"]!!) {
            get(NftOwnerBalance::total).isEqualTo(1L)
            get(NftOwnerBalance::byLevel).isEqualTo(mapOf(TokenLevel.Thunder to 1L))
        }
    }

    @Test
    fun `UNSTAKE reduces balance`() {
        val existing =
            NftOwnerBalance(
                owner = "0xowner1",
                total = 2,
                byLevel = mapOf(TokenLevel.Strength to 1, TokenLevel.Thunder to 1),
                blockNumber = 8,
                blockId = "b8",
                blockTimestamp = 800,
            )

        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns listOf(existing)

        val events = listOf(mockEvent("b1", 10, 1000, "STARGATE_UNSTAKE", 1, "0xowner1"))

        val result = service.processEvents(events)
        expectThat(result).hasSize(1)
        expectThat(result[0]) {
            get(NftOwnerBalance::owner).isEqualTo("0xowner1")
            get(NftOwnerBalance::total).isEqualTo(1L)
            get(NftOwnerBalance::byLevel)
                .isEqualTo(mapOf(TokenLevel.Strength to 0L, TokenLevel.Thunder to 1L))
        }
    }

    @Test
    fun `loads existing balances from repository`() {
        val existing =
            NftOwnerBalance(
                owner = "0xowner1",
                total = 3,
                byLevel = mapOf(TokenLevel.Strength to 2, TokenLevel.Thunder to 1),
                blockNumber = 5,
                blockId = "b5",
                blockTimestamp = 500,
            )

        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns listOf(existing)

        val events = listOf(mockEvent("b1", 10, 1000, "STARGATE_STAKE", 1, "0xowner1"))

        val result = service.processEvents(events)
        expectThat(result).hasSize(1)
        expectThat(result[0]) {
            get(NftOwnerBalance::total).isEqualTo(4L)
            get(NftOwnerBalance::byLevel)
                .isEqualTo(mapOf(TokenLevel.Strength to 3L, TokenLevel.Thunder to 1L))
        }

        verify { repository.findLatestBalancesBeforeBlock(match { it.contains("0xowner1") }, 10L) }
    }

    @Test
    fun `throws on missing levelId`() {
        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns emptyList()

        val events = listOf(mockEvent("b1", 10, 1000, "STARGATE_STAKE", null))

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Missing levelId in event params")
    }

    @Test
    fun `throws on unknown event type`() {
        every { repository.findLatestBalancesBeforeBlock(any(), any()) } returns emptyList()

        val events = listOf(mockEvent("b1", 10, 1000, "UNKNOWN_EVENT", 1))

        val ex = assertThrows<IllegalArgumentException> { service.processEvents(events) }
        expectThat(ex.message).isEqualTo("Unknown eventType: UNKNOWN_EVENT")
    }

    @Test
    fun `saveRecords delegates to repository`() {
        val records =
            listOf(
                NftOwnerBalance(
                    owner = "0xowner1",
                    total = 1,
                    byLevel = mapOf(TokenLevel.Strength to 1L),
                    blockNumber = 10,
                    blockId = "b10",
                    blockTimestamp = 1000,
                )
            )

        every { repository.saveAll(records) } returns records

        service.saveRecords(records)

        verify(exactly = 1) { repository.saveAll(records) }
    }
}
