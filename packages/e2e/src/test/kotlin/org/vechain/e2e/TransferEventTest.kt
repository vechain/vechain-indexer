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
