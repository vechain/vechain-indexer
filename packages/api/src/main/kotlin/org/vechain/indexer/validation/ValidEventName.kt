package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.history.HistoryEventName
import org.vechain.indexer.history.HistoryUtils.mapInputToNew

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

// TODO: Remove this when veworld have migrated to V2 History API
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [LegacyEventNameValidator::class])
@MustBeDocumented
annotation class LegacyValidEventName(
    val message: String = "Invalid event name. See HistoryEventName for the full list.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

// TODO: Remove this when veworld have migrated to V2 History API
class LegacyEventNameValidator : ConstraintValidator<LegacyValidEventName, List<String>?> {
    private val validator = EventNameValidator()

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        return validator.isValid(mapInputToNew(value), context)
    }
}
