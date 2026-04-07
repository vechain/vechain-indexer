package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ValidStargateTokenHistoryEventNameTest {
    private val validator = StargateTokenHistoryEventNameValidator()

    @Test
    fun `accepts null and empty values`() {
        expect {
            that(validator.isValid(null, DummyContext())).isTrue()
            that(validator.isValid(emptyList(), DummyContext())).isTrue()
        }
    }

    @Test
    fun `accepts supported Stargate token history events`() {
        expect {
            that(
                    validator.isValid(
                        listOf(
                            "STARGATE_UNSTAKE",
                            "STARGATE_DELEGATE_REQUEST_CANCELLED",
                            "NFT_SALE",
                            "VEVOTE_VOTE_CAST",
                        ),
                        DummyContext(),
                    )
                )
                .isTrue()
        }
    }

    @Test
    fun `rejects events outside Stargate token history scope`() {
        expect {
            that(validator.isValid(listOf("TRANSFER_FT", "B3TR_UPGRADE_GM"), DummyContext()))
                .isFalse()
        }
    }
}
