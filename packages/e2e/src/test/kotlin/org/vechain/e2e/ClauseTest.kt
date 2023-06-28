package org.vechain.e2e

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.rest.COUNT_LIMIT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class ClauseTest {
    @Test
    fun `get clauses for address`() {
        val clauses = VeWorldAPIClient.getClauses("0x435933c8064b4ae76be665428e0307ef2ccfbd68")
        expectThat(clauses.data).hasSize(13)
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

            that(clauses.pagination.hasCount).isTrue()
            that(clauses.pagination.countLimit).isEqualTo(COUNT_LIMIT)
            that(clauses.pagination.totalElements).isEqualTo(13)
            that(clauses.pagination.totalPages).isEqualTo(3)
            that(clauses.pagination.hasNext).isFalse()
        }
    }
}
