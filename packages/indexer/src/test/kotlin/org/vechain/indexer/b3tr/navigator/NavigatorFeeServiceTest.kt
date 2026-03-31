package org.vechain.indexer.b3tr.navigator

import io.mockk.MockKAnnotations
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures.buildIndexedEvent

@ExtendWith(MockKExtension::class)
internal class NavigatorFeeServiceTest {

    @MockK lateinit var repository: NavigatorFeeRepository

    private lateinit var service: NavigatorFeeService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NavigatorFeeService(repository)
    }

    @Test
    fun `processEvents returns empty list for empty input`() {
        assertEquals(emptyList<NavigatorFee>(), service.processEvents(emptyList()))
    }

    @Test
    fun `maps FeeDeposited event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "FeeDeposited",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("navigator" to "0xnav1", "roundId" to "5", "amount" to "1000")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("5", result[0].roundId)
        assertEquals("1000", result[0].amount)
        assertEquals("FeeDeposited", result[0].eventType)
    }

    @Test
    fun `maps FeeClaimed event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "FeeClaimed",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("navigator" to "0xnav1", "roundId" to "5", "amount" to "800")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("5", result[0].roundId)
        assertEquals("800", result[0].amount)
        assertEquals("FeeClaimed", result[0].eventType)
    }

    @Test
    fun `maps NavigatorFeeTaken event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "NavigatorFeeTaken",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "navigator" to "0xnav1",
                                    "citizen" to "0xcitizen1",
                                    "fee" to "200",
                                    "cycle" to "3",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("0xcitizen1", result[0].citizen)
        assertEquals("200", result[0].amount)
        assertEquals("3", result[0].roundId)
    }

    @Test
    fun `generates unique IDs for different events`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "FeeDeposited",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "100")
                        ),
                ),
                buildIndexedEvent(
                    id = "e2",
                    txId = "tx-2",
                    eventType = "FeeDeposited",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("navigator" to "0xnav2", "roundId" to "2", "amount" to "200")
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
                    eventType = "FeeDeposited",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("navigator" to "0xnav1", "roundId" to "1", "amount" to "100")
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
