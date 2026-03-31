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
internal class NavigatorEventServiceTest {

    @MockK lateinit var repository: NavigatorEventRepository

    private lateinit var service: NavigatorEventService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service = NavigatorEventService(repository)
    }

    @Test
    fun `processEvents returns empty list for empty input`() {
        assertEquals(emptyList<NavigatorEvent>(), service.processEvents(emptyList()))
    }

    @Test
    fun `maps NavigatorRegistered event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "NavigatorRegistered",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "navigator" to "0xnav1",
                                    "stakeAmount" to "5000",
                                    "metadataURI" to "ipfs://meta1",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("5000", result[0].stakeAmount)
        assertEquals("ipfs://meta1", result[0].metadataURI)
        assertEquals("NavigatorRegistered", result[0].eventType)
    }

    @Test
    fun `maps StakeAdded event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "StakeAdded",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("navigator" to "0xnav1", "amount" to "2000")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("2000", result[0].stakeAmount)
    }

    @Test
    fun `maps StakeWithdrawn event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "StakeWithdrawn",
                    params =
                        AbiEventParameters(
                            returnValues = mapOf("navigator" to "0xnav1", "amount" to "1000")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("0xnav1", result[0].navigator)
        assertEquals("1000", result[0].stakeAmount)
    }

    @Test
    fun `maps ExitAnnounced event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "ExitAnnounced",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "navigator" to "0xnav1",
                                    "announcedAtRound" to "10",
                                    "effectiveRound" to "12",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("10", result[0].announcedAtRound)
        assertEquals("12", result[0].effectiveRound)
    }

    @Test
    fun `maps NavigatorSlashed event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "NavigatorSlashed",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "navigator" to "0xnav1",
                                    "amount" to "500",
                                    "reason" to "misconduct",
                                    "remainingStake" to "4500",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("500", result[0].slashAmount)
        assertEquals("misconduct", result[0].slashReason)
        assertEquals("4500", result[0].remainingStake)
    }

    @Test
    fun `maps MetadataURIUpdated event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "MetadataURIUpdated",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf("navigator" to "0xnav1", "newURI" to "ipfs://meta2")
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("ipfs://meta2", result[0].metadataURI)
    }

    @Test
    fun `maps ReportSubmitted event correctly`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "ReportSubmitted",
                    params =
                        AbiEventParameters(
                            returnValues =
                                mapOf(
                                    "navigator" to "0xnav1",
                                    "roundId" to "5",
                                    "reportURI" to "ipfs://report1",
                                )
                        ),
                )
            )

        val result = service.processEvents(events)

        assertEquals(1, result.size)
        assertEquals("5", result[0].reportRoundId)
        assertEquals("ipfs://report1", result[0].reportURI)
    }

    @Test
    fun `generates unique IDs for different events`() {
        val events =
            listOf(
                buildIndexedEvent(
                    id = "e1",
                    txId = "tx-1",
                    eventType = "NavigatorRegistered",
                    params = AbiEventParameters(returnValues = mapOf("navigator" to "0xnav1")),
                ),
                buildIndexedEvent(
                    id = "e2",
                    txId = "tx-2",
                    eventType = "NavigatorRegistered",
                    params = AbiEventParameters(returnValues = mapOf("navigator" to "0xnav2")),
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
                    eventType = "NavigatorRegistered",
                    params = AbiEventParameters(returnValues = mapOf("navigator" to "0xnav1")),
                )
            )

        val result = service.processEvents(events)

        assertEquals("block-abc", result[0].blockId)
        assertEquals(42L, result[0].blockNumber)
        assertEquals(9999L, result[0].blockTimestamp)
        assertEquals("tx-1", result[0].txId)
    }
}
