package org.vechain.indexer.utils

import kotlin.reflect.full.memberProperties
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query

/**
 * Utility class for cursor-based keyset pagination with MongoDB.
 *
 * Cursor format: "sortValue|cursorFieldValue" The cursor points to the first record of the next
 * page.
 */
object CursorPaginationUtils {

    data class CursorPaginationParams(
        val pageSize: Int,
        val sortDirection: Sort.Direction,
        val sortByField: String,
        val cursor: String?,
        val cursorField: String = "entity",
    )

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
     * Parses the sort value string into its appropriate type based on the sort field name.
     *
     * @param sortValue The sort value as a string
     * @param sortByField The sort field name
     * @return The parsed value in its appropriate type (Long, BigDecimal, or String)
     */
    fun parseSortValue(sortValue: String, sortByField: String): Any {
        return when {
            sortByField.contains("actionsRewarded") -> sortValue.toLongOrNull() ?: sortValue
            sortByField.contains("totalRewardAmount") -> sortValue.toBigDecimalOrNull() ?: sortValue
            else -> sortValue
        }
    }

    /**
     * Applies cursor filtering to a MongoDB query for keyset pagination.
     *
     * Implements proper keyset pagination with tie-breaking:
     * - For DESC: (sortField < value) OR (sortField == value AND cursorField <= cursorValue)
     * - For ASC: (sortField > value) OR (sortField == value AND cursorField >= cursorValue)
     *
     * @param query The query to apply cursor filtering to
     * @param params The cursor pagination parameters
     */
    fun applyCursorFilter(query: Query, params: CursorPaginationParams) {
        val cursorInfo = parseCursor(params.cursor) ?: return

        val parsedSortValue = parseSortValue(cursorInfo.sortValue, params.sortByField)

        if (params.sortDirection == Sort.Direction.DESC) {
            // For DESC: (sortField < value) OR (sortField == value AND cursorField <= cursorValue)
            val cond1 = Criteria.where(params.sortByField).lt(parsedSortValue)
            val cond2 =
                Criteria.where(params.sortByField)
                    .`is`(parsedSortValue)
                    .and(params.cursorField)
                    .lte(cursorInfo.cursorValue)
            val orCriteria = Criteria().orOperator(cond1, cond2)
            query.addCriteria(orCriteria)
        } else {
            // For ASC: (sortField > value) OR (sortField == value AND cursorField >= cursorValue)
            val cond1 = Criteria.where(params.sortByField).gt(parsedSortValue)
            val cond2 =
                Criteria.where(params.sortByField)
                    .`is`(parsedSortValue)
                    .and(params.cursorField)
                    .gte(cursorInfo.cursorValue)
            val orCriteria = Criteria().orOperator(cond1, cond2)
            query.addCriteria(orCriteria)
        }
    }

    /**
     * Builds a complete keyset pagination query with filtering, sorting, and cursor handling.
     *
     * @param baseCriteria The base filtering criteria
     * @param size The page size
     * @param direction The sort direction ("ASC" or "DESC")
     * @param sortByField The field to sort by
     * @param cursor The cursor from the previous page (optional)
     * @param cursorField The field to use for tie-breaking (default: "entity")
     * @return A pair of (pageSize, Query)
     */
    fun buildCursorQuery(
        baseCriteria: Criteria,
        size: Int?,
        direction: String?,
        sortByField: String,
        cursor: String? = null,
        cursorField: String,
    ): Pair<Int, Query> {
        val pageSize = size ?: 20
        val sortDir =
            if (direction?.uppercase() == "ASC") Sort.Direction.ASC else Sort.Direction.DESC

        val query = Query(baseCriteria)

        // Apply cursor filtering
        val params =
            CursorPaginationParams(
                pageSize = pageSize,
                sortDirection = sortDir,
                sortByField = sortByField,
                cursor = cursor,
                cursorField = cursorField,
            )
        applyCursorFilter(query, params)

        // Apply sort and limit
        query.with(Sort.by(sortDir, sortByField).and(Sort.by(Sort.Direction.ASC, cursorField)))
        query.limit(pageSize + 1)

        return pageSize to query
    }

    /**
     * Calculates the next cursor for pagination based on the results.
     *
     * @param results The list of results returned from the query
     * @param pageSize The current page size
     * @param sortByField The name of the sort field to extract from the result item
     * @param cursorField The name of the cursor field to extract from the result item
     * @return The next cursor string, or null if there are no more results
     */
    fun <T> calculateNextCursor(
        results: List<T>,
        pageSize: Int,
        sortByField: String,
        cursorField: String,
    ): String? {
        if (results.size <= pageSize) return null

        val nextItem = results[pageSize]
        val kClass = nextItem!!::class

        // Extract sort value by field name using reflection
        val sortValue =
            kClass.memberProperties.find { it.name == sortByField }?.getter?.call(nextItem)
                ?: return null

        // Extract cursor value by field name using reflection
        val cursorValue =
            kClass.memberProperties
                .find { it.name == cursorField }
                ?.getter
                ?.call(nextItem)
                ?.toString() ?: return null

        return generateCursor(sortValue, cursorValue)
    }
}
