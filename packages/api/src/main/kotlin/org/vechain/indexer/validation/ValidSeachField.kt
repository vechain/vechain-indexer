package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [SearchFieldsValidator::class])
@MustBeDocumented
annotation class ValidSearchFields(
    val message: String = "Invalid search fields. Allowed values: [to, from, origin, gasPayer]",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class SearchFieldsValidator : ConstraintValidator<ValidSearchFields, List<String>?> {

    companion object {
        private val ALLOWED_FIELDS = setOf("to", "from", "origin", "gasPayer")
    }

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrEmpty()) return true // Allow null (defaults to all fields in controller)

        val invalidFields = value.filterNot { it in ALLOWED_FIELDS }
        return invalidFields.isEmpty()
    }
}
