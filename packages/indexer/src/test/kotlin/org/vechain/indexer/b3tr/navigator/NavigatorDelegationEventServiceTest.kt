package org.vechain.indexer.b3tr.navigator

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_CREATED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_DECREASED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_INCREASED
import org.vechain.indexer.b3tr.navigator.NavigatorService.Companion.DELEGATION_REMOVED
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

internal class NavigatorDelegationEventServiceTest {
    private val service = NavigatorDelegationEventService()
    private val nav = "0xaaaa111111111111111111111111111111111111"
    private val citizen = "0xcccc111111111111111111111111111111111111"

    private fun event(type: String, vararg params: Pair<String, Any>): IndexedEvent =
        buildIndexedEvent(
            id = "log-$type",
            txId = "0x" + "dd".repeat(32),
            blockId = "0x" + "01".repeat(32),
            blockNumber = 100L,
            blockTimestamp = 1000L,
            eventType = type,
            params =
                AbiEventParameters(
                    mapOf("citizen" to citizen.uppercase(), "navigator" to nav, *params),
                    type,
                ),
        )

    @Test
    fun `each delegation event records the delegation after it and what it moved`() {
        val rows =
            service.processEvents(
                listOf(
                    event(DELEGATION_CREATED, "amount" to "100"),
                    event(DELEGATION_INCREASED, "addedAmount" to "50", "newTotal" to "150"),
                    event(DELEGATION_DECREASED, "removedAmount" to "30", "newTotal" to "120"),
                    event(DELEGATION_REMOVED, "amount" to "120"),
                    event("B3TR_StakeAdded", "amount" to "1", "newTotal" to "2"),
                )
            )

        assertEquals(listOf("100", "150", "120", "0"), rows.map { it.amount.toPlainString() })
        assertEquals(listOf("100", "50", "-30", "-120"), rows.map { it.delta.toPlainString() })
        assertEquals(4, rows.map { it.id }.toSet().size)
        assertTrue(rows.all { it.citizen == citizen && it.navigator == nav && it.id.length == 40 })
        assertEquals(BigDecimal("-120"), rows.last().delta)
        assertEquals(1000L, rows.last().blockTimestamp)
    }
}
