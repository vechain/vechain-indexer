package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [SearchByValidator::class])
@MustBeDocumented
annotation class ValidSearchBy(
    val message: String = "Invalid search by field. Allowed values are: to, from, origin, gasPayer",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class SearchByValidator : ConstraintValidator<ValidSearchBy, List<String>?> {
    private val allowedValues = setOf("to", "from", "origin", "gasPayer")

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.isEmpty()) {
            return true
        }

        return value.all { it in allowedValues }
    }
}
