package org.vechain.indexer.history

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.vechain.indexer.history.HistoryFixtures.FULL
import org.vechain.indexer.history.HistoryFixtures.address
import org.vechain.indexer.postgres.PostgresHex
import org.vechain.indexer.validator.Status

/** Pins every encoder: bare sha1 id, `0x` hex, base-10 quantities, JSON for structured fields. */
class HistoryRowMappingTest {

    private fun roundTrip(e: IndexedHistoryEvent) =
        HistoryRowMapping.assemble(HistoryRowMapping.flatten(e).event)

    @Test
    fun `a fully populated event survives flatten and assemble`() {
        assertEquals(FULL, roundTrip(FULL))
    }

    @Test
    fun `an event with only the required fields survives too`() {
        val bare = HistoryFixtures.event(1, 5, HistoryEventName.UNKNOWN_TX, origin = null)
        assertEquals(bare, roundTrip(bare))
    }

    @Test
    fun `the id is twenty bytes rendered without a prefix`() {
        val row = HistoryRowMapping.flatten(FULL).event
        assertEquals(20, row.id.size)
        assertEquals(FULL.id, roundTrip(FULL).id)
        assertEquals(32, row.appId!!.size)
    }

    @Test
    fun `quantities decode from base 10 or Thor hex and render in base 10`() {
        assertEquals(
            BigDecimal("1000000000000000000"),
            HistoryRowMapping.numeric("0xde0b6b3a7640000"),
        )
        assertEquals(
            BigDecimal("1000000000000000000"),
            HistoryRowMapping.numeric("1000000000000000000"),
        )
        assertEquals(
            "1000000000000000000",
            HistoryRowMapping.decimal(BigDecimal("1000000000000000000")),
        )
        assertEquals(HistoryFixtures.uint256Max, roundTrip(FULL).tokenId)
        assertNull(HistoryRowMapping.numeric(null))
    }

    @Test
    fun `token ids decoded as numbers upstream still render as strings`() {
        @Suppress("UNCHECKED_CAST")
        val numbers = listOf(java.math.BigInteger.ONE, java.math.BigInteger.TEN) as List<String>
        assertEquals(listOf("1", "10"), roundTrip(FULL.copy(tokenIds = numbers)).tokenIds)
    }

    @Test
    fun `the address fan-out is the distinct union of the five address fields`() {
        val rows =
            HistoryRowMapping.flatten(FULL.copy(gasPayer = FULL.origin, owner = null)).addresses
        assertEquals(
            listOf(FULL.origin, FULL.to, FULL.from),
            rows.map { PostgresHex.hex(it.address) },
        )
        rows.forEach {
            assertEquals(FULL.blockTimestamp, it.blockTimestamp)
            assertEquals(FULL.blockNumber, it.blockNumber)
            assertEquals(FULL.eventName, it.eventName)
            assertEquals(FULL.id, PostgresHex.bareHex(it.eventId))
        }
        assertEquals(
            listOf(address(10)),
            HistoryRowMapping.flatten(HistoryFixtures.event(1, 1)).addresses.map {
                PostgresHex.hex(it.address)
            },
        )
    }

    @Test
    fun `lifecycle status is stored by ordinal, so the enum order is part of the schema`() {
        assertEquals(
            listOf(Status.NONE, Status.QUEUED, Status.ACTIVE, Status.EXITING, Status.EXITED),
            Status.entries,
        )
        assertEquals(3.toShort(), HistoryRowMapping.flatten(FULL).event.lifecycleStatus)
    }

    @Test
    fun `a malformed address fails loudly rather than storing something else`() {
        assertThrows(Exception::class.java) { HistoryRowMapping.flatten(FULL.copy(to = "not-hex")) }
    }
}
