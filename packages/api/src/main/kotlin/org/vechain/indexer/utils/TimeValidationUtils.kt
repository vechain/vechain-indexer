package org.vechain.indexer.utils

import org.vechain.indexer.exception.BadRequestException

object TimeValidationUtils {
    /**
     * Validate the timestamps provided for a time range query.
     *
     * @param after The timestamp to start the query from.
     * @param before The timestamp to end the query at.
     * @throws BadRequestException If the timestamps are invalid.
     */
    fun validateTimestamps(after: Long?, before: Long?) {
        if (after != null && before != null && after > before) {
            throw BadRequestException(
                "Invalid time range: 'after' timestamp is greater than 'before'"
            )
        }
        if (after != null && after < 0) {
            throw BadRequestException("Invalid 'after' timestamp: cannot be negative")
        }
        if (before != null && before < 0) {
            throw BadRequestException("Invalid 'before' timestamp: cannot be negative")
        }
    }
}
