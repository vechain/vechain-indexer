package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NonNegativeLongValidator::class])
@MustBeDocumented
annotation class ValidNonNegativeLong(
    val message: String = "Value must be non-negative (>= 0)",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NonNegativeLongValidator : ConstraintValidator<ValidNonNegativeLong, Long?> {
    override fun isValid(value: Long?, context: ConstraintValidatorContext): Boolean {
        return value == null || value >= 0
    }
}
