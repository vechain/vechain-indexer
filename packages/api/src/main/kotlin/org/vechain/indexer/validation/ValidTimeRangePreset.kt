package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.timeseries.TimeRangePreset

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ValidTimeRangePresetValidator::class])
@MustBeDocumented
annotation class ValidTimeRangePreset(
    val message: String =
        "The provided range is invalid. The allowed values are [\"1-hour\", \"1-day\", \"1-week\", \"1-month\", \"1-year\", \"all\"]",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ValidTimeRangePresetValidator : ConstraintValidator<ValidTimeRangePreset, String> {
    override fun isValid(
        value: String,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return try {
            TimeRangePreset.fromPathValue(value)
            true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}
