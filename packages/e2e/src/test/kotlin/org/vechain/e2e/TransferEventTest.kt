package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.IndexedTransferEvent
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty

class TransferEventTest {
    @Test
    fun `get transfer events for address`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEvents(address = "0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(transferEvents.data).hasSize(16)

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
        }

        val tokenAddress =
            transferEvents.data.find { te: IndexedTransferEvent -> te.tokenAddress != null }!!.tokenAddress

        // Get transfer event by token address
        val transferEventsForToken = VeWorldAPIClient.getTransferEvents(
            tokenAddress = tokenAddress,
        )

        expectThat(transferEventsForToken.data.size).isGreaterThan(0)

        transferEventsForToken.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
        }
    }

    @Test
    fun `get transfer events for address with pagination`() {
        val transferEvents =
            VeWorldAPIClient.getTransferEvents("0x435933c8064b4ae76be665428e0307ef2ccfbd68", size = 1)

        expectThat(transferEvents.data).hasSize(1)

        transferEvents.data.forEach { transferEvent: IndexedTransferEvent ->
            assertValidTransferEvent(transferEvent)
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