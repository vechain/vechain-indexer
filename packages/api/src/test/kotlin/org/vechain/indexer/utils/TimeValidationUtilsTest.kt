package org.vechain.indexer.utils

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.vechain.indexer.exception.BadRequestException
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class TimeValidationUtilsTest {
    @Test
    fun `validateTimestamps accepts zero`() {
        assertDoesNotThrow { TimeValidationUtils.validateTimestamps(0, 0) }
    }

    @Test
    fun `validateTimestamps accepts maximum supported unix timestamp`() {
        assertDoesNotThrow {
            TimeValidationUtils.validateTimestamps(
                TimeValidationUtils.MAX_SUPPORTED_UNIX_TIMESTAMP_LONG,
                TimeValidationUtils.MAX_SUPPORTED_UNIX_TIMESTAMP_LONG,
                "startTimestamp",
                "endTimestamp",
            )
        }
    }

    @Test
    fun `validateTimestamps rejects oversized unix timestamp with actual parameter name`() {
        val exception =
            assertThrows<BadRequestException> {
                TimeValidationUtils.validateTimestamps(
                    0,
                    31556889832780800L,
                    "startTimestamp",
                    "endTimestamp",
                )
            }

        expectThat(exception.message)
            .isEqualTo("Invalid 'endTimestamp' timestamp: exceeds supported Unix timestamp range")
    }

    @Test
    fun `validateTimestamps rejects reversed range with default parameter names`() {
        val exception =
            assertThrows<BadRequestException> { TimeValidationUtils.validateTimestamps(2, 1) }

        expectThat(exception.message)
            .isEqualTo("Invalid time range: 'after' timestamp is greater than 'before'")
    }

    @Test
    fun `validateTimestamps rejects oversized default after parameter name`() {
        val exception =
            assertThrows<BadRequestException> {
                TimeValidationUtils.validateTimestamps(31556889832780800L, null)
            }

        expectThat(exception.message)
            .isEqualTo("Invalid 'after' timestamp: exceeds supported Unix timestamp range")
    }
}
