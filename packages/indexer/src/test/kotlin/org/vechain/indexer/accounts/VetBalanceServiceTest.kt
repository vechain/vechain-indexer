package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class VetBalanceServiceTest {
    @MockK lateinit var repository: VetBalanceRepository

    private lateinit var service: VetBalanceService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = VetBalanceService(repository)
    }

    private fun vetTransferEvent(
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        from: String,
        to: String,
        amount: String,
    ) =
        buildIndexedEvent(
            id = "evt-$blockNumber-$from-$to",
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            eventType = "VET_TRANSFER",
            params =
                AbiEventParameters(
                    returnValues = mapOf("from" to from, "to" to to, "amount" to amount)
                ),
        )

    @Test
    fun `processEvents creates one record per address with updated balances`() {
        val blockId = "0xBLOCK"
        val blockNumber = 10L
        val blockTimestamp = 1000L
        val from = "0xFROM"
        val to = "0xTO"

        every { repository.findFirstByAddressOrderByBlockTimestampDesc(from) } returns
            VetBalance(
                address = from,
                blockId = "0xOLD",
                blockNumber = 9L,
                blockTimestamp = 900L,
                balance = BigInteger("100"),
            )
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(to) } returns
            VetBalance(
                address = to,
                blockId = "0xOLD",
                blockNumber = 9L,
                blockTimestamp = 900L,
                balance = BigInteger("50"),
            )

        val records =
            service.processEvents(
                listOf(
                    vetTransferEvent(
                        blockId,
                        blockNumber,
                        blockTimestamp,
                        from = from,
                        to = to,
                        amount = "10",
                    )
                )
            )

        val byAddress = records.associateBy { it.address }
        assertEquals(2, byAddress.size)

        assertEquals(blockId, byAddress[from]!!.blockId)
        assertEquals(blockNumber, byAddress[from]!!.blockNumber)
        assertEquals(blockTimestamp, byAddress[from]!!.blockTimestamp)
        assertEquals(BigInteger("90"), byAddress[from]!!.balance)

        assertEquals(blockId, byAddress[to]!!.blockId)
        assertEquals(blockNumber, byAddress[to]!!.blockNumber)
        assertEquals(blockTimestamp, byAddress[to]!!.blockTimestamp)
        assertEquals(BigInteger("60"), byAddress[to]!!.balance)
    }

    @Test
    fun `processEvents aggregates multiple transfers within the same block`() {
        val blockId = "0xBLOCK"
        val blockNumber = 10L
        val blockTimestamp = 1000L

        val a = "0xA"
        val b = "0xB"
        val c = "0xC"

        every { repository.findFirstByAddressOrderByBlockTimestampDesc(a) } returns
            VetBalance(a, "0xOLD", 9L, 900L, BigInteger("100"))
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(b) } returns
            VetBalance(b, "0xOLD", 9L, 900L, BigInteger("0"))
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(c) } returns
            VetBalance(c, "0xOLD", 9L, 900L, BigInteger("7"))

        // A -> B (10), C -> A (5) => A: -5, B: +10, C: -5
        val records =
            service.processEvents(
                listOf(
                    vetTransferEvent(
                        blockId,
                        blockNumber,
                        blockTimestamp,
                        from = a,
                        to = b,
                        amount = "10",
                    ),
                    vetTransferEvent(
                        blockId,
                        blockNumber,
                        blockTimestamp,
                        from = c,
                        to = a,
                        amount = "5",
                    ),
                )
            )

        val byAddress = records.associateBy { it.address }
        assertEquals(3, byAddress.size)
        assertEquals(BigInteger("95"), byAddress[a]!!.balance)
        assertEquals(BigInteger("10"), byAddress[b]!!.balance)
        assertEquals(BigInteger("2"), byAddress[c]!!.balance)
    }

    @Test
    fun `processEvents ignores non VET_TRANSFER events`() {
        val records =
            service.processEvents(
                listOf(
                    buildIndexedEvent(
                        eventType = "SomeOtherEvent",
                        params = AbiEventParameters(returnValues = emptyMap()),
                    )
                )
            )

        assertEquals(0, records.size)
    }
}
