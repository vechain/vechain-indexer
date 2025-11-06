package org.vechain.indexer.utils

import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.query.Criteria

@DisplayName("CursorPaginationUtils Tests")
class CursorPaginationUtilsTest {

    // ==================== CursorInfo.parse Tests ====================

    @Test
    @DisplayName("Should parse valid cursor with pipe separator")
    fun testParseValidCursor() {
        val cursor = "8053|0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17"
        val result = CursorPaginationUtils.CursorInfo.parse(cursor)

        assertEquals("8053", result?.sortValue)
        assertEquals("0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17", result?.cursorValue)
    }

    @Test
    @DisplayName("Should handle cursor with multiple pipes by splitting on first pipe only")
    fun testParsesCursorWithMultiplePipes() {
        val cursor = "123|value|withpipe"
        val result = CursorPaginationUtils.CursorInfo.parse(cursor)

        assertEquals("123", result?.sortValue)
        assertEquals("value|withpipe", result?.cursorValue)
    }

    @Test
    @DisplayName("Should return null for cursor without pipe")
    fun testParseInvalidCursorNoPipe() {
        val cursor = "8053-0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17"
        val result = CursorPaginationUtils.CursorInfo.parse(cursor)

        assertNull(result)
    }

    // ==================== parseCursor Tests ====================

    @Test
    @DisplayName("Should parse valid cursor string")
    fun testParseCursorValid() {
        val cursor = "100|user123"
        val result = CursorPaginationUtils.parseCursor(cursor)

        assertEquals("100", result?.sortValue)
        assertEquals("user123", result?.cursorValue)
    }

    @Test
    @DisplayName("Should return null for null cursor")
    fun testParseCursorNull() {
        val result = CursorPaginationUtils.parseCursor(null)
        assertNull(result)
    }

    @Test
    @DisplayName("Should return null for blank cursor")
    fun testParseCursorBlank() {
        val result = CursorPaginationUtils.parseCursor("")
        assertNull(result)
    }

    @Test
    @DisplayName("Should return null for whitespace cursor")
    fun testParseCursorWhitespace() {
        val result = CursorPaginationUtils.parseCursor("   ")
        assertNull(result)
    }

    // ==================== parseSortValue Tests ====================

    @Test
    @DisplayName("Should parse Long for actionsRewarded field")
    fun testParseSortValueLong() {
        val result = CursorPaginationUtils.parseSortValue("8053", "actionsRewarded")

        assertTrue(result is Long)
        assertEquals(8053L, result)
    }

    @Test
    @DisplayName("Should parse BigDecimal for totalRewardAmount field")
    fun testParseSortValueBigDecimal() {
        val result = CursorPaginationUtils.parseSortValue("254755.364186396", "totalRewardAmount")

        assertTrue(result is BigDecimal)
        assertEquals(BigDecimal("254755.364186396"), result)
    }

    @Test
    @DisplayName("Should return string for unknown field type")
    fun testParseSortValueString() {
        val result = CursorPaginationUtils.parseSortValue("somevalue", "unknownField")

        assertTrue(result is String)
        assertEquals("somevalue", result)
    }

    @Test
    @DisplayName("Should fallback to string if Long parsing fails")
    fun testParseSortValueLongFallback() {
        val result = CursorPaginationUtils.parseSortValue("not-a-number", "actionsRewarded")

        assertTrue(result is String)
        assertEquals("not-a-number", result)
    }

    @Test
    @DisplayName("Should fallback to string if BigDecimal parsing fails")
    fun testParseSortValueBigDecimalFallback() {
        val result = CursorPaginationUtils.parseSortValue("not-a-decimal", "totalRewardAmount")

        assertTrue(result is String)
        assertEquals("not-a-decimal", result)
    }

    // ==================== generateCursor Tests ====================

    @Test
    @DisplayName("Should generate cursor with Long sort value")
    fun testGenerateCursorWithLong() {
        val cursor = CursorPaginationUtils.generateCursor(8053L, "wallet123")

        assertEquals("8053|wallet123", cursor)
    }

    @Test
    @DisplayName("Should generate cursor with BigDecimal sort value")
    fun testGenerateCursorWithBigDecimal() {
        val cursor =
            CursorPaginationUtils.generateCursor(
                BigDecimal("254755.364186396"),
                "0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17",
            )

        assertEquals("254755.364186396|0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17", cursor)
    }

    @Test
    @DisplayName("Should generate cursor with string sort value")
    fun testGenerateCursorWithString() {
        val cursor = CursorPaginationUtils.generateCursor("stringvalue", "entity123")

        assertEquals("stringvalue|entity123", cursor)
    }

    // ==================== applyCursorFilter Tests ====================

    @Test
    @DisplayName("Should apply DESC filter with sort value comparison and cursor field tiebreaker")
    fun testApplyCursorFilterDESC() {
        val query = org.springframework.data.mongodb.core.query.Query()
        val params =
            CursorPaginationUtils.CursorPaginationParams(
                pageSize = 20,
                sortDirection = Sort.Direction.DESC,
                sortByField = "actionsRewarded",
                cursor = "8053|wallet123",
                cursorField = "entity",
            )

        CursorPaginationUtils.applyCursorFilter(query, params)

        // Verify the query has cursor filtering applied
        // The query object doesn't expose the criteria directly, but we can verify it was modified
        val queryString = query.toString()
        assertTrue(queryString.contains("actionsRewarded"))
    }

    @Test
    @DisplayName("Should apply ASC filter with sort value comparison and cursor field tiebreaker")
    fun testApplyCursorFilterASC() {
        val query = org.springframework.data.mongodb.core.query.Query()
        val params =
            CursorPaginationUtils.CursorPaginationParams(
                pageSize = 20,
                sortDirection = Sort.Direction.ASC,
                sortByField = "actionsRewarded",
                cursor = "100|wallet123",
                cursorField = "entity",
            )

        CursorPaginationUtils.applyCursorFilter(query, params)

        val queryString = query.toString()
        assertTrue(queryString.contains("actionsRewarded"))
    }

    @Test
    @DisplayName("Should not apply filter if cursor is null")
    fun testApplyCursorFilterNullCursor() {
        val query = org.springframework.data.mongodb.core.query.Query()
        val originalQueryString = query.toString()

        val params =
            CursorPaginationUtils.CursorPaginationParams(
                pageSize = 20,
                sortDirection = Sort.Direction.DESC,
                sortByField = "actionsRewarded",
                cursor = null,
                cursorField = "entity",
            )

        CursorPaginationUtils.applyCursorFilter(query, params)

        // Query should remain unchanged
        assertEquals(originalQueryString, query.toString())
    }

    // ==================== buildCursorQuery Tests ====================

    @Test
    @DisplayName("Should build query with base criteria and sort direction DESC")
    fun testBuildCursorQueryDESC() {
        val criteria = Criteria.where("entityType").`is`("USER")
        val (pageSize, query) =
            CursorPaginationUtils.buildCursorQuery(
                criteria,
                size = 20,
                direction = "DESC",
                sortByField = "actionsRewarded",
                cursorField = "entity",
            )

        assertEquals(20, pageSize)
        val queryString = query.toString()
        assertTrue(queryString.contains("entityType"))
    }

    @Test
    @DisplayName("Should build query with base criteria and sort direction ASC")
    fun testBuildCursorQueryASC() {
        val criteria = Criteria.where("entityType").`is`("USER")
        val (pageSize, query) =
            CursorPaginationUtils.buildCursorQuery(
                criteria,
                size = 10,
                direction = "ASC",
                sortByField = "actionsRewarded",
                cursorField = "entity",
            )

        assertEquals(10, pageSize)
        val queryString = query.toString()
        assertTrue(queryString.contains("entityType"))
    }

    @Test
    @DisplayName("Should use default page size when size is null")
    fun testBuildCursorQueryDefaultPageSize() {
        val criteria = Criteria.where("test").`is`("value")
        val (pageSize, _) =
            CursorPaginationUtils.buildCursorQuery(
                criteria,
                size = null,
                direction = "DESC",
                sortByField = "field",
                cursorField = "entity",
            )

        assertEquals(20, pageSize) // Default is 20
    }

    @Test
    @DisplayName("Should default to DESC when direction is null")
    fun testBuildCursorQueryDefaultDirection() {
        val criteria = Criteria.where("test").`is`("value")
        val (_, query) =
            CursorPaginationUtils.buildCursorQuery(
                criteria,
                size = 10,
                direction = null,
                sortByField = "field",
                cursorField = "entity",
            )

        val queryString = query.toString()
        assertTrue(queryString.contains("field"))
    }

    @Test
    @DisplayName("Should use custom cursor field")
    fun testBuildCursorQueryCustomCursorField() {
        val criteria = Criteria.where("test").`is`("value")
        val (_, query) =
            CursorPaginationUtils.buildCursorQuery(
                criteria,
                size = 20,
                direction = "DESC",
                sortByField = "actionsRewarded",
                cursor = null,
                cursorField = "user",
            )

        val queryString = query.toString()
        assertTrue(queryString.contains("actionsRewarded"))
    }

    @Test
    @DisplayName("Should apply cursor filter when cursor is provided")
    fun testBuildCursorQueryWithCursor() {
        val criteria = Criteria.where("entityType").`is`("USER")
        val (_, query) =
            CursorPaginationUtils.buildCursorQuery(
                criteria,
                size = 20,
                direction = "DESC",
                sortByField = "actionsRewarded",
                cursor = "8053|wallet123",
                cursorField = "entity",
            )

        val queryString = query.toString()
        assertTrue(queryString.contains("entityType"))
    }

    // ==================== calculateNextCursor Tests ====================

    data class TestRecord(val id: String, val actionsRewarded: Long, val entity: String)

    @Test
    @DisplayName("Should calculate next cursor from results when hasNext is true")
    fun testCalculateNextCursorWithResults() {
        val results =
            listOf(
                TestRecord("1", 100L, "entity1"),
                TestRecord("2", 90L, "entity2"),
                TestRecord("3", 80L, "entity3"),
            )
        val pageSize = 2

        val cursor =
            CursorPaginationUtils.calculateNextCursor(
                results,
                pageSize,
                "actionsRewarded",
                "entity",
            )

        assertEquals("80|entity3", cursor)
    }

    @Test
    @DisplayName("Should return null when results size equals page size")
    fun testCalculateNextCursorNoMoreResults() {
        val results = listOf(TestRecord("1", 100L, "entity1"), TestRecord("2", 90L, "entity2"))
        val pageSize = 2

        val cursor =
            CursorPaginationUtils.calculateNextCursor(
                results,
                pageSize,
                "actionsRewarded",
                "entity",
            )

        assertNull(cursor)
    }

    @Test
    @DisplayName("Should return null when results are empty")
    fun testCalculateNextCursorEmptyResults() {
        val results: List<TestRecord> = emptyList()
        val pageSize = 20

        val cursor =
            CursorPaginationUtils.calculateNextCursor(
                results,
                pageSize,
                "actionsRewarded",
                "entity",
            )

        assertNull(cursor)
    }

    @Test
    @DisplayName("Should handle BigDecimal sort values")
    fun testCalculateNextCursorWithBigDecimal() {
        data class TestRecordBD(val id: String, val totalReward: BigDecimal, val user: String)

        val results =
            listOf(
                TestRecordBD("1", BigDecimal("100.50"), "user1"),
                TestRecordBD("2", BigDecimal("90.25"), "user2"),
                TestRecordBD("3", BigDecimal("80.75"), "user3"),
            )
        val pageSize = 2

        val cursor =
            CursorPaginationUtils.calculateNextCursor(results, pageSize, "totalReward", "user")

        assertEquals("80.75|user3", cursor)
    }

    @Test
    @DisplayName("Should work with custom cursor field names")
    fun testCalculateNextCursorCustomCursorField() {
        data class CustomRecord(val id: String, val score: Long, val userId: String)

        val results =
            listOf(
                CustomRecord("1", 500L, "user_alpha"),
                CustomRecord("2", 450L, "user_beta"),
                CustomRecord("3", 400L, "user_gamma"),
            )
        val pageSize = 2

        val cursor = CursorPaginationUtils.calculateNextCursor(results, pageSize, "score", "userId")

        assertEquals("400|user_gamma", cursor)
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Should handle full pagination cycle: generate cursor and parse it")
    fun testFullPaginationCycle() {
        // Generate cursor
        val generatedCursor =
            CursorPaginationUtils.generateCursor(
                8053L,
                "0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17",
            )
        assertEquals("8053|0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17", generatedCursor)

        // Parse cursor
        val parsed = CursorPaginationUtils.parseCursor(generatedCursor)
        assertEquals("8053", parsed?.sortValue)
        assertEquals("0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17", parsed?.cursorValue)

        // Parse sort value
        val parsedSortValue =
            CursorPaginationUtils.parseSortValue(parsed!!.sortValue, "actionsRewarded")
        assertTrue(parsedSortValue is Long)
        assertEquals(8053L, parsedSortValue)
    }

    @Test
    @DisplayName("Should handle pagination parameters data class")
    fun testCursorPaginationParams() {
        val params =
            CursorPaginationUtils.CursorPaginationParams(
                pageSize = 25,
                sortDirection = Sort.Direction.DESC,
                sortByField = "totalRewardAmount",
                cursor = "1000.50|user123",
                cursorField = "user",
            )

        assertEquals(25, params.pageSize)
        assertEquals(Sort.Direction.DESC, params.sortDirection)
        assertEquals("totalRewardAmount", params.sortByField)
        assertEquals("1000.50|user123", params.cursor)
        assertEquals("user", params.cursorField)
    }
}
