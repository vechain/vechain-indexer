package org.vechain.indexer.transfer

import io.mockk.mockk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.mongodb.core.MongoTemplate
import org.vechain.indexer.event.model.generic.AbiEventParameters
import org.vechain.indexer.fixtures.IndexedEventsFixtures
import org.vechain.indexer.thor.Address
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo

internal class FungibleTokenInteractionsServiceTest {

    private val fixture = IndexedEventsFixtures
    private lateinit var service: FungibleTokenInteractionsService

    @BeforeEach
    fun setUp() {
        val mockMongoTemplate = mockk<MongoTemplate>()
        service = FungibleTokenInteractionsService(mockMongoTemplate)
    }

    @Test
    fun `processEvents - should create interactions for both sender and receiver`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                blockId = "0xblock123",
                blockNumber = 100L,
                blockTimestamp = 1000L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123", "value" to "1000")
                                as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).hasSize(2)
        expectThat(result)
            .containsExactlyInAnyOrder(
                FungibleTokenInteraction(
                    contractAddress = "0xcontract123",
                    blockId = "0xblock123",
                    blockNumber = 100L,
                    blockTimestamp = 1000L,
                    walletAddress = "0xfrom123",
                ),
                FungibleTokenInteraction(
                    contractAddress = "0xcontract123",
                    blockId = "0xblock123",
                    blockNumber = 100L,
                    blockTimestamp = 1000L,
                    walletAddress = "0xto123",
                ),
            )
    }

    @Test
    fun `processEvents - should filter out events with null address`() {
        val event =
            fixture.buildIndexedEvent(
                address = null,
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should filter out events with missing from address`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should filter out events with missing to address`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("from" to "0xfrom123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should deduplicate same contractAddress and walletAddress pairs`() {
        val event1 =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                blockId = "0xblock123",
                blockNumber = 100L,
                blockTimestamp = 1000L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser1", "to" to "0xuser2") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val event2 =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                blockId = "0xblock124",
                blockNumber = 101L,
                blockTimestamp = 1001L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser1", "to" to "0xuser2") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event1, event2))

        // Should only have 2 unique interactions (0xuser1 and 0xuser2 with 0xcontract123)
        expectThat(result).hasSize(2)
        expectThat(result.map { it.walletAddress }.toSet()).hasSize(2)
    }

    @Test
    fun `processEvents - should keep interactions with different walletAddresses for same contract`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).hasSize(2)
        expectThat(result.all { it.contractAddress == "0xcontract123" }).isEqualTo(true)
        expectThat(result.map { it.walletAddress })
            .containsExactlyInAnyOrder("0xfrom123", "0xto123")
    }

    @Test
    fun `processEvents - should keep interactions for same wallet across different contracts`() {
        val event1 =
            fixture.buildIndexedEvent(
                address = "0xcontract1",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser", "to" to "0xother1") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val event2 =
            fixture.buildIndexedEvent(
                address = "0xcontract2",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser", "to" to "0xother2") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event1, event2))

        // Should have 4 interactions: user appears in both contracts
        expectThat(result).hasSize(4)
        expectThat(result.filter { it.walletAddress == "0xuser" }).hasSize(2)
    }

    @Test
    fun `processEvents - should throw when event type is not Transfer`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "NotTransfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123") as Map<String, Any>,
                        eventType = "NotTransfer",
                    ),
            )

        assertThrows<IllegalArgumentException> { service.processEvents(listOf(event)) }
    }

    @Test
    fun `processEvents - should handle empty event list`() {
        val result = service.processEvents(emptyList())

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should preserve block metadata`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                blockId = "0xblock999",
                blockNumber = 999L,
                blockTimestamp = 9999L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).hasSize(2)
        expectThat(result.all { it.blockId == "0xblock999" }).isEqualTo(true)
        expectThat(result.all { it.blockNumber == 999L }).isEqualTo(true)
        expectThat(result.all { it.blockTimestamp == 9999L }).isEqualTo(true)
    }

    @Test
    fun `processEvents - should handle self-transfer (from equals to)`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser123", "to" to "0xuser123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        // Self-transfer should still be deduplicated to 1 interaction
        expectThat(result).hasSize(1)
        expectThat(result.single().walletAddress).isEqualTo("0xuser123")
    }

    @Test
    fun `processEvents - should handle multiple transfers with mixed duplicates`() {
        val event1 =
            fixture.buildIndexedEvent(
                address = "0xcontract1",
                eventType = "Transfer",
                blockId = "0xblock1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuserA", "to" to "0xuserB") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val event2 =
            fixture.buildIndexedEvent(
                address = "0xcontract1",
                eventType = "Transfer",
                blockId = "0xblock1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuserB", "to" to "0xuserA") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val event3 =
            fixture.buildIndexedEvent(
                address = "0xcontract1",
                eventType = "Transfer",
                blockId = "0xblock1",
                blockNumber = 1L,
                blockTimestamp = 100L,
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuserA", "to" to "0xuserB") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event1, event2, event3))

        // Should have 2 unique pairs: (contract, userA) and (contract, userB)
        expectThat(result).hasSize(2)
        expectThat(result.map { it.walletAddress }.toSet()).hasSize(2)
    }

    @Test
    fun `processEvents - should handle from and to with null values in params`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues = mapOf("from" to null, "to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should handle type casting for from and to addresses`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf(
                                "from" to 12345 as Any, // Wrong type - should be string
                                "to" to "0xto123",
                            )
                                as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should filter out VTHO contract address`() {
        val event =
            fixture.buildIndexedEvent(
                address = VTHO_CONTRACT_ADDRESS,
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should include non-VTHO contracts and filter VTHO`() {
        val vthoEvent =
            fixture.buildIndexedEvent(
                address = VTHO_CONTRACT_ADDRESS,
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to "0xto123") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val otherEvent =
            fixture.buildIndexedEvent(
                address = "0xothercontract",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom456", "to" to "0xto456") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(vthoEvent, otherEvent))

        // Should only have interactions from the non-VTHO contract
        expectThat(result).hasSize(2)
        expectThat(result.all { it.contractAddress == "0xothercontract" }).isEqualTo(true)
    }

    @Test
    fun `processEvents - should filter out ZERO_ADDRESS for from`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to Address.ZERO_ADDRESS, "to" to "0xto123")
                                as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        // Should only have interaction for 'to' address, not 'from'
        expectThat(result).hasSize(1)
        expectThat(result.single().walletAddress).isEqualTo("0xto123")
    }

    @Test
    fun `processEvents - should filter out ZERO_ADDRESS for to`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xfrom123", "to" to Address.ZERO_ADDRESS)
                                as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        // Should only have interaction for 'from' address, not 'to'
        expectThat(result).hasSize(1)
        expectThat(result.single().walletAddress).isEqualTo("0xfrom123")
    }

    @Test
    fun `processEvents - should filter out both addresses when both are ZERO_ADDRESS`() {
        val event =
            fixture.buildIndexedEvent(
                address = "0xcontract123",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to Address.ZERO_ADDRESS, "to" to Address.ZERO_ADDRESS)
                                as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(event))

        // Should have no interactions since both addresses are zero
        expectThat(result).isEmpty()
    }

    @Test
    fun `processEvents - should handle mixed events with ZERO_ADDRESS and VTHO`() {
        val zeroAddressEvent =
            fixture.buildIndexedEvent(
                address = "0xcontract1",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to Address.ZERO_ADDRESS, "to" to "0xuser1")
                                as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val vthoEvent =
            fixture.buildIndexedEvent(
                address = VTHO_CONTRACT_ADDRESS,
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser2", "to" to "0xuser3") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )
        val validEvent =
            fixture.buildIndexedEvent(
                address = "0xcontract2",
                eventType = "Transfer",
                params =
                    AbiEventParameters(
                        returnValues =
                            mapOf("from" to "0xuser4", "to" to "0xuser5") as Map<String, Any>,
                        eventType = "Transfer",
                    ),
            )

        val result = service.processEvents(listOf(zeroAddressEvent, vthoEvent, validEvent))

        // Should have: 1 from zero event (0xuser1) + 0 from VTHO + 2 from valid event
        expectThat(result).hasSize(3)
        expectThat(result.map { it.walletAddress })
            .containsExactlyInAnyOrder("0xuser1", "0xuser4", "0xuser5")
    }
}
