package org.vechain.indexer.utils

import java.util.Base64

/**
 * Utility object for keyset (cursor-based) pagination with PostgreSQL.
 *
 * Uses Base64 encoding for cursor strings to hide implementation details from API consumers and
 * ensure URL-safe cursor values.
 *
 * Keyset pagination is more performant than offset pagination for large datasets as it doesn't
 * require scanning and skipping rows.
 */
object KeysetPaginationUtils {

    data class CursorInfo(val sortValue: String, val entityId: String) {
        companion object {
            /**
             * Parses a Base64-encoded cursor string into its components.
             *
             * @param cursor The Base64-encoded cursor string
             * @return CursorInfo if valid, null otherwise
             */
            fun fromCursor(cursor: String): CursorInfo? {
                return try {
                    val decoded = String(Base64.getDecoder().decode(cursor))
                    val parts = decoded.split("|", limit = 2)
                    if (parts.size == 2) {
                        CursorInfo(sortValue = parts[0], entityId = parts[1])
                    } else {
                        null
                    }
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    /**
     * Encodes a sort value and entity ID into a Base64-encoded cursor string.
     *
     * @param sortValue The value of the sort field (e.g., total_reward_amount)
     * @param entityId The unique identifier for the entity (for tie-breaking)
     * @return A Base64-encoded cursor string
     */
    fun encodeCursor(sortValue: Any, entityId: String): String {
        return Base64.getEncoder().encodeToString("$sortValue|$entityId".toByteArray())
    }

    /**
     * Decodes a Base64-encoded cursor string into its components.
     *
     * @param cursor The Base64-encoded cursor string
     * @return A Pair of (sortValue, entityId) or null if invalid
     */
    fun decodeCursor(cursor: String): Pair<String, String>? {
        val info = CursorInfo.fromCursor(cursor) ?: return null
        return info.sortValue to info.entityId
    }

    /**
     * Parses a cursor string and extracts the CursorInfo.
     *
     * @param cursor The Base64-encoded cursor string
     * @return CursorInfo if valid, null otherwise
     */
    fun parseCursor(cursor: String?): CursorInfo? {
        if (cursor.isNullOrBlank()) return null
        return CursorInfo.fromCursor(cursor)
    }

    /**
     * Validates that a cursor is well-formed.
     *
     * @param cursor The cursor string to validate
     * @return true if the cursor is valid, false otherwise
     */
    fun isValidCursor(cursor: String?): Boolean {
        if (cursor.isNullOrBlank()) return false
        return CursorInfo.fromCursor(cursor) != null
    }

    /**
     * Calculates the next cursor from a list of results.
     *
     * Uses the last item of the current page to generate the cursor for the next page.
     *
     * @param results The list of results from the query
     * @param pageSize The page size used in the query
     * @param getSortValue Function to extract the sort value from a result item
     * @param getEntityId Function to extract the entity ID from a result item
     * @return The next cursor string, or null if there are no more pages
     */
    fun <T> calculateNextCursor(
        results: List<T>,
        pageSize: Int,
        getSortValue: (T) -> Any,
        getEntityId: (T) -> String,
    ): String? {
        // If we got more results than pageSize, there's a next page
        if (results.size <= pageSize) return null

        // Use the last item of the current page (not the extra one)
        val lastItem = results[pageSize - 1]
        val sortValue = getSortValue(lastItem)
        val entityId = getEntityId(lastItem)

        return encodeCursor(sortValue, entityId)
    }
}
