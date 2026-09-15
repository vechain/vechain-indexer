package org.vechain.indexer.utils

import kotlin.reflect.full.memberProperties

/**
 * Utility class for cursor-based keyset pagination.
 *
 * Cursor format: "sortValue|cursorFieldValue" The cursor points to the first record of the next
 * page.
 */
object CursorPaginationUtils {

    data class CursorInfo(val sortValue: String, val cursorValue: String) {
        companion object {
            fun parse(cursor: String): CursorInfo? {
                if (!cursor.contains("|")) return null
                val parts = cursor.split("|", limit = 2)
                return CursorInfo(sortValue = parts[0], cursorValue = parts[1])
            }
        }
    }

    /**
     * Generates a cursor string from a record's sort value and cursor field value.
     *
     * @param sortValue The value of the sort field for the record
     * @param cursorFieldValue The value of the cursor field for the record
     * @return A cursor string in the format "sortValue|cursorFieldValue"
     */
    fun generateCursor(sortValue: Any, cursorFieldValue: String): String {
        return "$sortValue|$cursorFieldValue"
    }

    /**
     * Parses a cursor string into its components.
     *
     * @param cursor The cursor string to parse
     * @return A CursorInfo object or null if the cursor is invalid
     */
    fun parseCursor(cursor: String?): CursorInfo? {
        if (cursor.isNullOrBlank()) return null
        return CursorInfo.parse(cursor)
    }

    /**
     * Parses a cursor sort value, attempting to convert it to common numeric types. Falls back to
     * String if no conversion is possible.
     *
     * The caller is responsible for ensuring the cursor was generated with valid data.
     *
     * @param sortValue The sort value as a string
     * @return The parsed value as Long, BigDecimal, or String
     */
    fun parseSortValue(sortValue: String): Any {
        // Try parsing as Long first
        sortValue.toLongOrNull()?.let {
            return it
        }

        // Try parsing as BigDecimal next
        sortValue.toBigDecimalOrNull()?.let {
            return it
        }

        // Fall back to String
        return sortValue
    }

    /**
     * Validates that a cursor is well-formed. Returns true if the cursor is valid, false otherwise.
     *
     * A valid cursor must:
     * - Parse successfully (contain a pipe separator)
     * - Have a non-blank cursor field value
     *
     * @param cursor The cursor string to validate
     * @return true if the cursor is valid, false otherwise
     */
    fun isValidCursor(cursor: String?): Boolean {
        val cursorInfo = parseCursor(cursor) ?: return false

        // Validate that cursor value is not empty
        return cursorInfo.cursorValue.isNotBlank()
    }

    /**
     * Calculates the next cursor for pagination based on the results.
     *
     * The cursor points to the LAST record of the current page (not the first of the next page).
     * This allows proper tie-breaking: when filtering with the cursor, we exclude records up to and
     * including the cursor record, ensuring no duplicates or skips.
     *
     * @param results The list of results returned from the query
     * @param pageSize The current page size
     * @param sortByField The name of the sort field to extract from the result item
     * @param cursorField The name of the cursor field to extract from the result item
     * @return The next cursor string, or null if there are no more results
     * @throws IllegalArgumentException if sortByField or cursorField don't exist on the result type
     */
    fun <T> calculateNextCursor(
        results: List<T>,
        pageSize: Int,
        sortByField: String,
        cursorField: String,
    ): String? {
        if (results.size <= pageSize) return null

        val nextItem = results[pageSize - 1]
        val kClass = nextItem!!::class

        // Validate that the fields exist on the target object
        val sortProperty =
            kClass.memberProperties.find { it.name == sortByField }
                ?: throw IllegalArgumentException(
                    "Field '$sortByField' not found on ${kClass.simpleName}. Available fields: " +
                        "${kClass.memberProperties.map { it.name }.joinToString(", ")}"
                )

        val cursorProperty =
            kClass.memberProperties.find { it.name == cursorField }
                ?: throw IllegalArgumentException(
                    "Field '$cursorField' not found on ${kClass.simpleName}. Available fields: " +
                        "${kClass.memberProperties.map { it.name }.joinToString(", ")}"
                )

        // Extract sort value by field name using reflection
        val sortValue = sortProperty.getter.call(nextItem) ?: return null

        // Extract cursor value by field name using reflection
        val cursorValue = cursorProperty.getter.call(nextItem)?.toString() ?: return null

        return generateCursor(sortValue, cursorValue)
    }
}
