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

        // Validate well-formed cursor (contains pipe separator and non-blank cursor value)
        if (!CursorPaginationUtils.isValidCursor(value)) {
            return false
        }

        // Validate that sort value can be parsed as a number
        val cursorInfo = CursorPaginationUtils.parseCursor(value) ?: return false
        return cursorInfo.sortValue.toLongOrNull() != null ||
            cursorInfo.sortValue.toBigDecimalOrNull() != null
    }
}
