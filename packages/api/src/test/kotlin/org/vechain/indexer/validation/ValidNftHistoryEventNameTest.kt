package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ValidNftHistoryEventNameTest {
    private val validator = NftHistoryEventNameValidator()

    @Test
    fun `accepts null and empty values`() {
        expect {
            that(validator.isValid(null, DummyContext())).isTrue()
            that(validator.isValid(emptyList(), DummyContext())).isTrue()
        }
    }

    @Test
    fun `accepts supported nft history events`() {
        expect {
            that(validator.isValid(listOf("TRANSFER_NFT", "NFT_SALE"), DummyContext())).isTrue()
        }
    }

    @Test
    fun `rejects events outside nft history scope`() {
        expect {
            that(validator.isValid(listOf("VEVOTE_VOTE_CAST", "STARGATE_UNSTAKE"), DummyContext()))
                .isFalse()
        }
    }
}
