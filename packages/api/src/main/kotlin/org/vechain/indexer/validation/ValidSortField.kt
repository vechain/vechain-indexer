package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [SortFieldValidator::class])
@MustBeDocumented
annotation class ValidSortField(
    val allowedValues: Array<String>,
    val message: String = "Invalid sort field",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class SortFieldValidator : ConstraintValidator<ValidSortField, String> {
    private lateinit var allowedValues: Array<String>

    override fun initialize(annotation: ValidSortField) {
        this.allowedValues = annotation.allowedValues
    }

    override fun isValid(
        value: String?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        // Null values are invalid (sortBy should have a default value)
        if (value == null) {
            return false
        }

        // Check if value is in allowed list
        return value in allowedValues
    }
}
