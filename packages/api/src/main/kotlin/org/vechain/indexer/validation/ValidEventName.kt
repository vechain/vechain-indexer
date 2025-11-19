package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.history.HistoryEventName

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [EventNameValidator::class])
@MustBeDocumented
annotation class ValidEventName(
    val message: String = "Invalid event name. See HistoryEventName for the full list.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class EventNameValidator : ConstraintValidator<ValidEventName, List<String>?> {
    private val allowedValues = HistoryEventName.entries.map { it.name }.toSet()

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.isEmpty()) {
            return true
        }

        return value.all { it in allowedValues }
    }
}
