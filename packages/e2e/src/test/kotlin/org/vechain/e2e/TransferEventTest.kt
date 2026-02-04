package org.vechain.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.vechain.indexer.transfer.IndexedTransferEvent
import org.vechain.indexer.transfer.TransferEventType
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

class TransferEventTest {

    @BeforeEach
    fun `perform healthcheck`() {
        VeWorldAPIClient.performIndexerHealthCheck("TransferIndexer")
    }

    @Test
    fun `get transfer events for address`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )

        expectThat(transferEvents.data).hasSize(25)
        expectThat(transferEvents.pagination.hasNext).isFalse()

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
        }

        val tokenAddress =
            transferEvents.data
                .find { te: IndexedTransferEvent -> te.tokenAddress != null }!!
                .tokenAddress

        // Get transfer event by token address
        val transferEventsForToken = VeWorldAPIClient.getTransferEvents(tokenAddress = tokenAddress)

        expectThat(transferEventsForToken.data.size).isGreaterThan(0)

        transferEventsForToken.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
        }
    }

    @Test
    fun `get transfer events for address with pagination`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEvents(
                "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                size = 1,
            )

        expectThat(transferEvents.data).hasSize(1)
        expectThat(transferEvents.pagination.hasNext).isTrue()

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
        }
    }

    @Test
    fun `get transfer events filtered by eventType VET`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                eventType = TransferEventType.VET,
            )

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.eventType).isEqualTo(TransferEventType.VET)
        }
    }

    @Test
    fun `get transfer events filtered by eventType FUNGIBLE_TOKEN`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                eventType = TransferEventType.FUNGIBLE_TOKEN,
            )

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.eventType).isEqualTo(TransferEventType.FUNGIBLE_TOKEN)
        }
    }

    @Test
    fun `get transfer events from address filtered by eventType`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEventsFrom(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                eventType = TransferEventType.VET,
            )

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.eventType).isEqualTo(TransferEventType.VET)
            expectThat(transferEvent.from).isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
        }
    }

    @Test
    fun `get transfer events to address filtered by eventType`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEventsTo(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                eventType = TransferEventType.VET,
            )

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.eventType).isEqualTo(TransferEventType.VET)
            expectThat(transferEvent.to).isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
        }
    }

    @Test
    fun `get transfer events with after timestamp filter`() {
        // First get all events to find a timestamp to filter by
        val allEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )

        expectThat(allEvents.data.size).isGreaterThan(0)

        // Use the timestamp of a middle event as the "after" filter
        val middleIndex = allEvents.data.size / 2
        val afterTimestamp = allEvents.data[middleIndex].blockTimestamp

        val filteredEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                after = afterTimestamp,
            )

        // All returned events should have blockTimestamp >= afterTimestamp
        filteredEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.blockTimestamp).isGreaterThanOrEqualTo(afterTimestamp)
        }
    }

    @Test
    fun `get transfer events with before timestamp filter`() {
        // First get all events to find a timestamp to filter by
        val allEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )

        expectThat(allEvents.data.size).isGreaterThan(0)

        // Use the timestamp of a middle event as the "before" filter
        val middleIndex = allEvents.data.size / 2
        val beforeTimestamp = allEvents.data[middleIndex].blockTimestamp

        val filteredEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                before = beforeTimestamp,
            )

        // All returned events should have blockTimestamp <= beforeTimestamp
        filteredEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.blockTimestamp).isLessThanOrEqualTo(beforeTimestamp)
        }
    }

    @Test
    fun `get transfer events with both after and before timestamp filters`() {
        // First get all events to find timestamps to filter by
        val allEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )

        expectThat(allEvents.data.size).isGreaterThan(0)

        // Get timestamps for a range (results are typically sorted descending by blockNumber)
        val timestamps = allEvents.data.map { it.blockTimestamp }.sorted()
        val afterTimestamp = timestamps.first()
        val beforeTimestamp = timestamps.last()

        val filteredEvents =
            VeWorldAPIClient.getTransferEvents(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                after = afterTimestamp,
                before = beforeTimestamp,
            )

        // All returned events should have blockTimestamp within range
        filteredEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
            expectThat(transferEvent.blockTimestamp).isGreaterThanOrEqualTo(afterTimestamp)
            expectThat(transferEvent.blockTimestamp).isLessThanOrEqualTo(beforeTimestamp)
        }
    }

    @Test
    fun `get transfer events from address with timestamp filter`() {
        val allEvents =
            VeWorldAPIClient.getTransferEventsFrom(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )

        if (allEvents.data.isNotEmpty()) {
            val afterTimestamp = allEvents.data.last().blockTimestamp

            val filteredEvents =
                VeWorldAPIClient.getTransferEventsFrom(
                    address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                    after = afterTimestamp,
                )

            filteredEvents.data.forEach { transferEvent: IndexedTransferEvent ->
                assertValidTransferEvent(transferEvent)
                expectThat(transferEvent.from)
                    .isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
                expectThat(transferEvent.blockTimestamp).isGreaterThanOrEqualTo(afterTimestamp)
            }
        }
    }

    @Test
    fun `get transfer events to address with timestamp filter`() {
        val allEvents =
            VeWorldAPIClient.getTransferEventsTo(
                address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
            )

        if (allEvents.data.isNotEmpty()) {
            val beforeTimestamp = allEvents.data.first().blockTimestamp

            val filteredEvents =
                VeWorldAPIClient.getTransferEventsTo(
                    address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                    before = beforeTimestamp,
                )

            filteredEvents.data.forEach { transferEvent: IndexedTransferEvent ->
                assertValidTransferEvent(transferEvent)
                expectThat(transferEvent.to).isEqualTo("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
                expectThat(transferEvent.blockTimestamp).isLessThanOrEqualTo(beforeTimestamp)
            }
        }
    }

    fun assertValidTransferEvent(transferEvent: IndexedTransferEvent) {
        expect {
            that(transferEvent.id).isNotEmpty()
            that(transferEvent.from).isNotEmpty()
            that(transferEvent.to).isNotEmpty()
            that(transferEvent.blockNumber).isGreaterThan(0)
            that(transferEvent.txId).isNotEmpty()
        }
    }
}
