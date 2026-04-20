package org.vechain.indexer.b3tr.navigator

import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

internal class NavigatorDelegationEventServiceTest {
    private val service = NavigatorDelegationEventService(mockk(relaxed = true))

    @Test
    fun `delegation events fail fast when required params are missing`() {
        assertThrows(IllegalStateException::class.java) {
            service.processEvents(
                listOf(
                    buildIndexedEvent(
                        eventType = "B3TR_DelegationCreated",
                        params =
                            AbiEventParameters(
                                mapOf("navigator" to "0xnav1", "amount" to "100"),
                                "B3TR_DelegationCreated",
                            ),
                    )
                )
            )
        }
    }
}
