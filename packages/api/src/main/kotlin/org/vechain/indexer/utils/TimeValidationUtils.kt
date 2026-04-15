package org.vechain.indexer.utils

import org.vechain.indexer.exception.BadRequestException

object TimeValidationUtils {
    const val MAX_SUPPORTED_UNIX_TIMESTAMP = "31556889832694400"
    const val MAX_SUPPORTED_UNIX_TIMESTAMP_LONG = 31556889832694400L

    /**
     * Validate the timestamps provided for a time range query.
     *
     * @param after The timestamp to start the query from.
     * @param before The timestamp to end the query at.
     * @param afterName The field name to use for the `after` timestamp in validation error
     *   messages.
     * @param beforeName The field name to use for the `before` timestamp in validation error
     *   messages.
     * @throws BadRequestException If the timestamps are invalid.
     */
    fun validateTimestamps(
        after: Long?,
        before: Long?,
        afterName: String = "after",
        beforeName: String = "before",
    ) {
        if (after != null && after < 0) {
            throw BadRequestException("Invalid '$afterName' timestamp: cannot be negative")
        }
        if (before != null && before < 0) {
            throw BadRequestException("Invalid '$beforeName' timestamp: cannot be negative")
        }
        if (after != null && after > MAX_SUPPORTED_UNIX_TIMESTAMP_LONG) {
            throw BadRequestException(
                "Invalid '$afterName' timestamp: exceeds supported Unix timestamp range"
            )
        }
        if (before != null && before > MAX_SUPPORTED_UNIX_TIMESTAMP_LONG) {
            throw BadRequestException(
                "Invalid '$beforeName' timestamp: exceeds supported Unix timestamp range"
            )
        }
        if (after != null && before != null && after > before) {
            throw BadRequestException(
                "Invalid time range: '$afterName' timestamp is greater than '$beforeName'"
            )
        }
    }
}
