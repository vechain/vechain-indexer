package org.vechain.indexer.validation

import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

@DisplayName("ValidCursor Annotation Tests")
class ValidCursorTest {
    private lateinit var validator: CursorValidator
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setup() {
        validator = CursorValidator()
        context = mock(ConstraintValidatorContext::class.java)
    }

    // ==================== Null/Blank Cursor Tests ====================

    @Test
    @DisplayName("Should accept null cursor (first page)")
    fun testValidateNullCursor() {
        assertTrue(validator.isValid(null, context))
    }

    @Test
    @DisplayName("Should accept empty string cursor (first page)")
    fun testValidateEmptyCursor() {
        assertTrue(validator.isValid("", context))
    }

    @Test
    @DisplayName("Should accept blank cursor (whitespace only)")
    fun testValidateBlankCursor() {
        assertTrue(validator.isValid("   ", context))
    }

    // ==================== Well-Formed Cursor Tests ====================

    @Test
    @DisplayName("Should reject cursor without pipe separator")
    fun testValidateCursorWithoutPipe() {
        assertFalse(validator.isValid("0x123abc", context))
    }

    @Test
    @DisplayName("Should reject cursor with empty cursor field value")
    fun testValidateCursorWithEmptyCursorValue() {
        assertFalse(validator.isValid("100|", context))
    }

    @Test
    @DisplayName("Should reject cursor with whitespace-only cursor field value")
    fun testValidateCursorWithWhitespaceCursorValue() {
        assertFalse(validator.isValid("100|   ", context))
    }

    // ==================== Non-Numeric Sort Value Tests ====================

    @Test
    @DisplayName("Should reject cursor with non-numeric sort value (colon)")
    fun testValidateCursorWithNonNumericValue() {
        assertFalse(validator.isValid(":|wallet123", context))
    }

    @Test
    @DisplayName("Should reject cursor with alphabetic sort value")
    fun testValidateCursorWithAlphabeticSortValue() {
        assertFalse(validator.isValid("abc|wallet123", context))
    }

    @Test
    @DisplayName("Should reject cursor with mixed alphanumeric sort value")
    fun testValidateCursorWithMixedAlphanumericSortValue() {
        assertFalse(validator.isValid("123abc|wallet123", context))
    }

    // ==================== Plain Integer Detection Tests ====================

    @Test
    @DisplayName("Should reject cursor with plain integer sort value (0)")
    fun testValidateCursorWithPlainIntegerZero() {
        assertFalse(validator.isValid("0|wallet123", context))
    }

    @Test
    @DisplayName("Should reject cursor with plain integer sort value (positive)")
    fun testValidateCursorWithPlainIntegerPositive() {
        assertFalse(validator.isValid("100|wallet123", context))
    }

    @Test
    @DisplayName("Should reject cursor with plain integer sort value (large)")
    fun testValidateCursorWithPlainIntegerLarge() {
        assertFalse(validator.isValid("999999|wallet123", context))
    }

    // ==================== Valid Decimal Sort Value Tests ====================

    @Test
    @DisplayName("Should accept cursor with decimal sort value (0.0)")
    fun testValidateCursorWithDecimalZero() {
        assertTrue(validator.isValid("0.0|wallet123", context))
    }

    @Test
    @DisplayName("Should accept cursor with decimal sort value (positive)")
    fun testValidateCursorWithDecimalPositive() {
        assertTrue(validator.isValid("100.50|wallet123", context))
    }

    @Test
    @DisplayName("Should accept cursor with decimal sort value (many places)")
    fun testValidateCursorWithDecimalManyPlaces() {
        assertTrue(validator.isValid("254755.364186396|user456", context))
    }

    @Test
    @DisplayName("Should accept cursor with decimal sort value (trailing zero)")
    fun testValidateCursorWithDecimalTrailingZero() {
        assertTrue(validator.isValid("100.00|wallet123", context))
    }

    @Test
    @DisplayName("Should accept cursor with decimal sort value (leading zero)")
    fun testValidateCursorWithDecimalLeadingZero() {
        assertTrue(validator.isValid("0.50|wallet123", context))
    }

    // ==================== Cursor Field Value Tests ====================

    @Test
    @DisplayName("Should accept cursor with address-like field value")
    fun testValidateCursorWithAddressValue() {
        assertTrue(validator.isValid("100.50|0xe1556fabbdab0dbf4bcdbfac07e4d470e366fc17", context))
    }

    @Test
    @DisplayName("Should accept cursor with user ID field value")
    fun testValidateCursorWithUserIdValue() {
        assertTrue(validator.isValid("100.50|user_12345", context))
    }

    @Test
    @DisplayName("Should accept cursor with alphanumeric field value")
    fun testValidateCursorWithAlphanumericValue() {
        assertTrue(validator.isValid("100.50|abc123XYZ", context))
    }

    @Test
    @DisplayName("Should accept cursor with special characters in field value")
    fun testValidateCursorWithSpecialCharactersValue() {
        assertTrue(validator.isValid("100.50|0x_user-123", context))
    }

    // ==================== Integration Tests ====================

    @Test
    @DisplayName("Should accept valid cursor from pagination response")
    fun testValidateCursorFromRealPagination() {
        val realCursor = "100.477119375011837816|0x083266a111751ac99e050214c68a9bce39afd035"
        assertTrue(validator.isValid(realCursor, context))
    }

    @Test
    @DisplayName("Should accept cursor with multiple pipes (limit=2 split)")
    fun testValidateCursorWithMultiplePipes() {
        // parseCursor uses split("|", limit = 2) so "wallet|extra" is treated as the cursor value
        assertTrue(validator.isValid("100.50|wallet|extra", context))
    }

    @Test
    @DisplayName("Should handle very large decimal numbers")
    fun testValidateCursorWithVeryLargeDecimal() {
        assertTrue(validator.isValid("999999999999.999999999999|wallet123", context))
    }

    @Test
    @DisplayName("Should reject negative numbers without decimal")
    fun testValidateCursorWithNegativeIntegerValue() {
        assertFalse(validator.isValid("-100|wallet123", context))
    }

    @Test
    @DisplayName("Should accept negative numbers with decimal")
    fun testValidateCursorWithNegativeDecimalValue() {
        assertTrue(validator.isValid("-100.50|wallet123", context))
    }
}
