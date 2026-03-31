package org.vechain.indexer.b3tr.navigator

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class NavigatorDelegationServiceTest {

    @MockK lateinit var repository: NavigatorDelegationRepository

    private lateinit var service: NavigatorDelegationService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NavigatorDelegationService(repository)
    }

    @Test
    fun `processEvents returns empty list for empty input`() {
        assertEquals(emptyList<NavigatorDelegation>(), service.processEvents(emptyList()))
    }

    @Test
    fun `maps DelegationCreated event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "DelegationCreated",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "citizen" to "0xcitizen1",
                                    "navigator" to "0xnav1",
                                    "amount" to "3000",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xcitizen1", result[0].citizen)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("3000", result[0].amount)
        assertEquals("DelegationCreated", result[0].eventType)
    }

    @Test
    fun `maps DelegationUpdated event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "DelegationUpdated",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "citizen" to "0xcitizen1",
                                    "navigator" to "0xnav1",
                                    "newAmount" to "5000",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xcitizen1", result[0].citizen)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("5000", result[0].amount)
    }

    @Test
    fun `maps DelegationRemoved event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "DelegationRemoved",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("citizen" to "0xcitizen1", "navigator" to "0xnav1")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xcitizen1", result[0].citizen)
        assertEquals("0xnav1", result[0].navigator)
        assertNull(result[0].amount)
    }

    @Test
    fun `maps NavigatorVoteCast event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "NavigatorVoteCast",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "citizen" to "0xcitizen1",
                                    "navigator" to "0xnav1",
                                    "roundId" to "7",
                                    "appsIds" to "[app1, app2, app3]",
                                    "voteWeights" to "[100, 200, 300]",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xcitizen1", result[0].citizen)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("7", result[0].roundId)
        assertEquals(listOf("app1", "app2", "app3"), result[0].appIds)
        assertEquals(listOf("100", "200", "300"), result[0].voteWeights)
    }

    @Test
    fun `generates unique IDs for different events`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "DelegationCreated",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("citizen" to "0xcitizen1", "navigator" to "0xnav1")
                        ),
                ),
                buildIndexedEvent(
                    id = "e2",
                    txId = "tx-2",
                    eventType = "DelegationCreated",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("citizen" to "0xcitizen2", "navigator" to "0xnav2")
                        ),
                ),
            )

        val result = service.processEvents(events)

        assertEquals(2, result.size)
        assert(result[0].id != result[1].id)
    }

    @Test
    fun `populates block fields from event`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    blockId = "block-abc",
                    blockNumber = 42L,
                    blockTimestamp = 9999L,
                    txId = "tx-1",
                    eventType = "DelegationCreated",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("citizen" to "0xcitizen1", "navigator" to "0xnav1")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals("block-abc", result[0].blockId)
        assertEquals(42L, result[0].blockNumber)
        assertEquals(9999L, result[0].blockTimestamp)
        assertEquals("tx-1", result[0].txId)
    }
}
