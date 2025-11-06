package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.utils.CursorPaginationUtils

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [CursorValidator::class])
@MustBeDocumented
annotation class ValidCursor(
    val message: String = "Invalid cursor format",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class CursorValidator : ConstraintValidator<ValidCursor, String> {
    override fun isValid(
        value: String?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        // Null or blank cursors are valid (no cursor = first page)
        if (value.isNullOrBlank()) {
            return true
        }

        // Validate well-formed cursor
        if (!CursorPaginationUtils.isValidCursor(value)) {
            return false
        }

        // Additional check: ensure sort value is numeric for known numeric fields
        val cursorInfo = CursorPaginationUtils.parseCursor(value) ?: return false
        val sortValue = cursorInfo.sortValue

        // Validate that the sort value is a valid number (Long or BigDecimal)
        // This prevents MongoDB type conversion errors for numeric fields
        // A valid numeric sort value should either:
        // 1. Parse as Long/BigDecimal successfully, OR
        // 2. Contain a decimal point (for BigDecimal fields)
        if (!isValidNumericValue(sortValue)) {
            return false
        }

        return true
    }

    private fun isValidNumericValue(sortValue: String): Boolean {
        // Reject non-numeric strings
        if (sortValue.toLongOrNull() == null && sortValue.toBigDecimalOrNull() == null) {
            return false
        }

        // If it's a plain integer (no decimal point), reject it
        // This prevents MongoDB type conversion errors with Decimal128 fields
        if (sortValue.toLongOrNull() != null && !sortValue.contains(".")) {
            return false
        }

        // Accept all other numeric values (those with decimal points)
        return true
    }
}
