package org.vechain.indexer.accounts

import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import java.math.BigInteger
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.accounts.repository.VetBalanceRepository
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent
import org.vechain.indexer.utils.BlockDetails

@ExtendWith(MockKExtension::class)
internal class VetBalanceServiceTest {
    @MockK lateinit var repository: VetBalanceRepository

    private lateinit var service: TestableService

    private class TestableService(repository: VetBalanceRepository) :
        VetBalanceService(repository) {
        fun testFilterVetTransfers(
            events: List<org.vechain.indexer.event.model.generic.IndexedEvent>
        ) = filterVetTransfers(events)

        fun testParseVetTransfer(event: org.vechain.indexer.event.model.generic.IndexedEvent) =
            parseVetTransfer(event)

        fun testComputeDeltasByAddress(
            blockEvents: List<org.vechain.indexer.event.model.generic.IndexedEvent>
        ) = computeDeltasByAddress(blockEvents)

        fun testGetLatestBalance(
            address: String,
            latestBalanceByAddress: MutableMap<String, BigInteger>,
        ) = getLatestBalance(address, latestBalanceByAddress)

        fun testApplyDeltas(
            blockDetails: BlockDetails,
            deltasByAddress: Map<String, BigInteger>,
            latestBalanceByAddress: MutableMap<String, BigInteger>,
            results: MutableList<VetBalance>,
        ) = applyDeltas(blockDetails, deltasByAddress, latestBalanceByAddress, results)
    }

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = TestableService(repository)
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
    fun `processEvents uses latest computed balance across multiple blocks`() {
        val blockId1 = "0xBLOCK1"
        val blockId2 = "0xBLOCK2"
        val t1 = 1000L
        val t2 = 1010L
        val b1 = 10L
        val b2 = 11L

        val a = "0xA"
        val b = "0xB"
        val c = "0xC"

        every { repository.findFirstByAddressOrderByBlockTimestampDesc(a) } returns
            VetBalance(a, "0xOLD", 9L, 900L, BigInteger("100"))
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(b) } returns
            VetBalance(b, "0xOLD", 9L, 900L, BigInteger("0"))
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(c) } returns
            VetBalance(c, "0xOLD", 9L, 900L, BigInteger("0"))

        // Block 10: A -> B (10) => A: 90, B: 10
        // Block 11: B -> C (5)  => B: 5,  C: 5
        val records =
            service.processEvents(
                listOf(
                    vetTransferEvent(blockId1, b1, t1, from = a, to = b, amount = "10"),
                    vetTransferEvent(blockId2, b2, t2, from = b, to = c, amount = "5"),
                )
            )

        val byAddressByBlock =
            records
                .groupBy { it.blockNumber }
                .mapValues { (_, rs) -> rs.associateBy { it.address } }

        assertEquals(BigInteger("90"), byAddressByBlock[b1]!![a]!!.balance)
        assertEquals(BigInteger("10"), byAddressByBlock[b1]!![b]!!.balance)
        assertEquals(BigInteger("5"), byAddressByBlock[b2]!![b]!!.balance)
        assertEquals(BigInteger("5"), byAddressByBlock[b2]!![c]!!.balance)

        // Ensures we don't re-read "previous" from DB for the same address within the batch.
        verify(exactly = 1) { repository.findFirstByAddressOrderByBlockTimestampDesc(a) }
        verify(exactly = 1) { repository.findFirstByAddressOrderByBlockTimestampDesc(b) }
        verify(exactly = 1) { repository.findFirstByAddressOrderByBlockTimestampDesc(c) }
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

    @Test
    fun `filterVetTransfers keeps only VET_TRANSFER events`() {
        val transfers =
            service.testFilterVetTransfers(
                listOf(
                    buildIndexedEvent(eventType = "SomeOtherEvent"),
                    buildIndexedEvent(eventType = "VET_TRANSFER"),
                    buildIndexedEvent(eventType = "AnotherEvent"),
                )
            )

        assertEquals(1, transfers.size)
        assertEquals("VET_TRANSFER", transfers.single().eventType)
    }

    @Test
    fun `parseVetTransfer throws when from param missing`() {
        val event =
            buildIndexedEvent(
                id = "evt-1",
                eventType = "VET_TRANSFER",
                params = AbiEventParameters(returnValues = mapOf("to" to "0xTO", "amount" to "1")),
            )

        assertThrows(IllegalStateException::class.java) { service.testParseVetTransfer(event) }
    }

    @Test
    fun `computeDeltasByAddress aggregates and ignores self transfers`() {
        val blockId = "0xBLOCK"
        val blockNumber = 10L
        val blockTimestamp = 1000L

        val a = "0xA"
        val b = "0xB"

        val deltas =
            service.testComputeDeltasByAddress(
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
                        from = a,
                        to = a,
                        amount = "999",
                    ),
                    vetTransferEvent(
                        blockId,
                        blockNumber,
                        blockTimestamp,
                        from = a,
                        to = b,
                        amount = "5",
                    ),
                )
            )

        assertEquals(BigInteger("-15"), deltas[a])
        assertEquals(BigInteger("15"), deltas[b])
    }

    @Test
    fun `getLatestBalance caches repository lookup per batch`() {
        val address = "0xA"
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(address) } returns
            VetBalance(address, "0xOLD", 9L, 900L, BigInteger("123"))

        val cache = mutableMapOf<String, BigInteger>()
        assertEquals(BigInteger("123"), service.testGetLatestBalance(address, cache))
        assertEquals(BigInteger("123"), service.testGetLatestBalance(address, cache))

        verify(exactly = 1) { repository.findFirstByAddressOrderByBlockTimestampDesc(address) }
    }

    @Test
    fun `applyDeltas updates cache and emits VetBalance records`() {
        val a = "0xA"
        val b = "0xB"

        every { repository.findFirstByAddressOrderByBlockTimestampDesc(a) } returns
            VetBalance(a, "0xOLD", 9L, 900L, BigInteger("100"))
        every { repository.findFirstByAddressOrderByBlockTimestampDesc(b) } returns
            VetBalance(b, "0xOLD", 9L, 900L, BigInteger("0"))

        val blockDetails =
            BlockDetails(blockId = "0xBLOCK", blockNumber = 10L, blockTimestamp = 1000L)
        val deltas = linkedMapOf(a to BigInteger("-10"), b to BigInteger("10"))
        val cache = mutableMapOf<String, BigInteger>()
        val results = mutableListOf<VetBalance>()

        service.testApplyDeltas(blockDetails, deltas, cache, results)

        assertEquals(BigInteger("90"), cache[a])
        assertEquals(BigInteger("10"), cache[b])

        val byAddress = results.associateBy { it.address }
        assertEquals(BigInteger("90"), byAddress[a]!!.balance)
        assertEquals(BigInteger("10"), byAddress[b]!!.balance)
        assertEquals(10L, byAddress[a]!!.blockNumber)
        assertEquals(10L, byAddress[b]!!.blockNumber)
    }
}
