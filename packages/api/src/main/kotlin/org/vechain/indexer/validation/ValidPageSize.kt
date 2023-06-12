package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [PageSizeValidator::class])
@MustBeDocumented
annotation class ValidPageSize(
    val message: String = "The maximum allowed page size is $PAGE_SIZE_LIMIT",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class PageSizeValidator : ConstraintValidator<ValidPageSize, Int> {
    override fun isValid(value: Int?, constraintValidatorContext: ConstraintValidatorContext): Boolean {
        return value == null || value <= PAGE_SIZE_LIMIT
    }
}