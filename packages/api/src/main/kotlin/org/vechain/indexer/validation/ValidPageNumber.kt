package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PageNumberValidator::class])
annotation class ValidPageNumber(
    val message: String = "The page number must not be negative",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class PageNumberValidator : ConstraintValidator<ValidPageNumber, Int> {
    override fun isValid(
        value: Int?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || value >= 0
    }
}
