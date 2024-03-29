package org.vechain.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ClauseTest {

    @BeforeEach
    fun `perform healthcheck`() {
        VeWorldAPIClient.performIndexerHealthCheck("ClauseIndexer")
    }

    @Test
    fun `get clauses for address`() {
        val clauses = VeWorldAPIClient.getClauses("0x435933c8064b4ae76be665428e0307ef2ccfbd68")

        expectThat(clauses.data).hasSize(13)

        expectThat(clauses.pagination.hasNext).isFalse()
    }

    @Test
    fun `get clauses for address pagination`() {
        val clauses =
            VeWorldAPIClient.getClauses(
                "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                page = 0,
                size = 1
            )

        expectThat(clauses.data).hasSize(1)

        expectThat(clauses.pagination.hasNext).isTrue()
    }

    @Test
    fun `get clauses for address pagination detail`() {
        val clauses =
            VeWorldAPIClient.getClauses(
                "0x435933c8064b4ae76be665428e0307ef2ccfbd68",
                page = 2,
                size = 5
            )

        expect {
            that(clauses.data).hasSize(3)

            that(clauses.pagination.hasNext).isFalse()
        }
    }
}
