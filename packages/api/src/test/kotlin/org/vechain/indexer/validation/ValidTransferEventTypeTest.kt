package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.transfer.TransferEventType
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidTransferEventTypeTest {
    private val validator = TransferEventTypeValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts empty list`() {
        expect { that(validator.isValid(emptyList(), DummyContext())).isTrue() }
    }

    @Test
    fun `accepts single valid event type`() {
        TransferEventType.entries.forEach { eventType ->
            expect { that(validator.isValid(listOf(eventType.name), DummyContext())).isTrue() }
        }
    }

    @Test
    fun `accepts multiple valid event types`() {
        expect {
            that(validator.isValid(listOf("VET", "NFT"), DummyContext())).isTrue()
            that(validator.isValid(listOf("VET", "FUNGIBLE_TOKEN", "NFT"), DummyContext())).isTrue()
            that(
                    validator.isValid(
                        listOf("VET", "FUNGIBLE_TOKEN", "NFT", "SEMI_FUNGIBLE_TOKEN"),
                        DummyContext(),
                    )
                )
                .isTrue()
        }
    }

    @Test
    fun `rejects invalid event type`() {
        expect { that(validator.isValid(listOf("INVALID"), DummyContext())).isFalse() }
    }

    @Test
    fun `rejects list with one invalid type among valid ones`() {
        expect {
            that(validator.isValid(listOf("VET", "INVALID", "NFT"), DummyContext())).isFalse()
        }
    }

    @Test
    fun `rejects lowercase event type`() {
        expect { that(validator.isValid(listOf("vet"), DummyContext())).isFalse() }
    }

    @Test
    fun `rejects empty string in list`() {
        expect { that(validator.isValid(listOf("VET", "", "NFT"), DummyContext())).isFalse() }
    }
}
