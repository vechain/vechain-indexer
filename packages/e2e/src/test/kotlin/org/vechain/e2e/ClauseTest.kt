package org.vechain.e2e

import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isNotNull

class ClauseTest {
    @Test
    fun `get clauses for address`() {
        val clauses = VeWorldAPIClient.getClauses(
            "0x435933c8064b4ae76be665428e0307ef2ccfbd68"
        )
        expectThat(clauses.data)
            .isNotNull()
            .hasSize(13)
    }

    @Test
    fun `get clauses for address pagination`() {
        val clauses = VeWorldAPIClient.getClauses(
            "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
            page = 0,
            size = 1
        )
        expectThat(clauses.data)
            .isNotNull()
            .hasSize(1)
    }
}