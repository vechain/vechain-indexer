package org.vechain.e2e

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEmpty
import strikt.assertions.isNotEmpty

class FungiblesTest {
    @Test
    fun `should return some contracts`() {
        val contracts =
          VeWorldAPIClient.getFungibleTokenContracts("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(contracts).isNotEmpty()
    }

    @Test
    fun `should not return any contracts`() {
        val contracts =
          VeWorldAPIClient.getFungibleTokenContracts("0x435933c8064b4ae76be665428e0307ef2ccfbd69")

        expectThat(contracts).isEmpty()
    }
}
