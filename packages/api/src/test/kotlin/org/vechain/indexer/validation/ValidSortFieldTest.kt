package org.vechain.indexer.validation

import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@DisplayName("ValidSortField Annotation Tests")
class ValidSortFieldTest {
    private lateinit var validator: SortFieldValidator
    private lateinit var context: ConstraintValidatorContext

    @BeforeEach
    fun setup() {
        validator = SortFieldValidator()
        context = mock(ConstraintValidatorContext::class.java)
    }

    // ==================== Two-Value Allowed List Tests ====================

    @Test
    @DisplayName("Should accept first allowed value")
    fun testValidateFirstAllowedValue() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertTrue(validator.isValid("totalRewardAmount", context))
    }

    @Test
    @DisplayName("Should accept second allowed value")
    fun testValidateSecondAllowedValue() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertTrue(validator.isValid("actionsRewarded", context))
    }

    @Test
    @DisplayName("Should reject value not in allowed list")
    fun testValidateValueNotInList() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("invalidField", context))
    }

    @Test
    @DisplayName("Should reject null value")
    fun testValidateNullValue() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid(null, context))
    }

    // ==================== Case Sensitivity Tests ====================

    @Test
    @DisplayName("Should be case-sensitive (lowercase not allowed)")
    fun testValidateCaseSensitiveLowercase() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("totalrewardamount", context))
    }

    @Test
    @DisplayName("Should be case-sensitive (UPPERCASE not allowed)")
    fun testValidateCaseSensitiveUppercase() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("TOTALREWARDAMOUNT", context))
    }

    @Test
    @DisplayName("Should be case-sensitive (mixed case not allowed)")
    fun testValidateCaseSensitiveMixedCase() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("TotalRewardAmount", context))
    }

    // ==================== Empty/Whitespace String Tests ====================

    @Test
    @DisplayName("Should reject empty string")
    fun testValidateEmptyString() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("", context))
    }

    @Test
    @DisplayName("Should reject single character")
    fun testValidateSingleCharacter() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("a", context))
    }

    @Test
    @DisplayName("Should reject whitespace string")
    fun testValidateWhitespaceString() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("   ", context))
    }

    // ==================== Partial Match Tests ====================

    @Test
    @DisplayName("Should reject partial match (prefix)")
    fun testValidatePartialMatchPrefix() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("totalReward", context))
    }

    @Test
    @DisplayName("Should reject partial match (suffix)")
    fun testValidatePartialMatchSuffix() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("RewardAmount", context))
    }

    @Test
    @DisplayName("Should reject partial match (middle)")
    fun testValidatePartialMatchMiddle() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("Reward", context))
    }

    // ==================== Whitespace Tests ====================

    @Test
    @DisplayName("Should reject value with leading space")
    fun testValidateValueWithLeadingSpace() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid(" totalRewardAmount", context))
    }

    @Test
    @DisplayName("Should reject value with trailing space")
    fun testValidateValueWithTrailingSpace() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("totalRewardAmount ", context))
    }

    // ==================== Single Item List Tests ====================

    @Test
    @DisplayName("Should accept value in single-item allowed list")
    fun testValidateSingleItemListAccept() {
        initializeValidator("totalRewardAmount")
        assertTrue(validator.isValid("totalRewardAmount", context))
    }

    @Test
    @DisplayName("Should reject different value in single-item allowed list")
    fun testValidateSingleItemListReject() {
        initializeValidator("totalRewardAmount")
        assertFalse(validator.isValid("actionsRewarded", context))
    }

    // ==================== Multi-Item List Tests ====================

    @Test
    @DisplayName("Should accept first value in three-item list")
    fun testValidateMultipleItemsFirst() {
        initializeValidator("field1", "field2", "field3")
        assertTrue(validator.isValid("field1", context))
    }

    @Test
    @DisplayName("Should accept middle value in three-item list")
    fun testValidateMultipleItemsMiddle() {
        initializeValidator("field1", "field2", "field3")
        assertTrue(validator.isValid("field2", context))
    }

    @Test
    @DisplayName("Should accept last value in three-item list")
    fun testValidateMultipleItemsLast() {
        initializeValidator("field1", "field2", "field3")
        assertTrue(validator.isValid("field3", context))
    }

    @Test
    @DisplayName("Should reject value not in three-item list")
    fun testValidateMultipleItemsReject() {
        initializeValidator("field1", "field2", "field3")
        assertFalse(validator.isValid("field4", context))
    }

    // ==================== Real-World Scenario Tests ====================

    @Test
    @DisplayName("Should validate leaderboard sort fields correctly")
    fun testValidateLeaderboardSortFields() {
        initializeValidator("totalRewardAmount", "actionsRewarded")

        // Valid values
        assertTrue(validator.isValid("totalRewardAmount", context))
        assertTrue(validator.isValid("actionsRewarded", context))

        // Invalid values
        assertFalse(validator.isValid("totalReward", context))
        assertFalse(validator.isValid("actions", context))
        assertFalse(validator.isValid("reward", context))
        assertFalse(validator.isValid("sortBy", context))
    }

    @Test
    @DisplayName("Should reject SQL injection-like strings")
    fun testValidateSQLInjectionAttempt() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("totalRewardAmount; DROP TABLE", context))
    }

    @Test
    @DisplayName("Should reject special characters in sort field")
    fun testValidateSpecialCharacters() {
        initializeValidator("totalRewardAmount", "actionsRewarded")
        assertFalse(validator.isValid("total@Reward", context))
    }

    @Test
    @DisplayName("Should distinguish between similar field names")
    fun testValidateSimilarFieldNames() {
        initializeValidator("actions", "actionsRewarded")
        assertTrue(validator.isValid("actions", context))
        assertTrue(validator.isValid("actionsRewarded", context))
        assertFalse(validator.isValid("action", context))
    }

    // ==================== Helper Method ====================

    private fun initializeValidator(vararg allowedValues: String) {
        // Create mock annotation
        val annotation = mock(ValidSortField::class.java)
        // Configure the mock to return the allowed values
        `when`(annotation.allowedValues).thenReturn(allowedValues as Array<String>)

        validator.initialize(annotation)
    }
}
