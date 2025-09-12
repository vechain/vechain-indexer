package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ISODateStringValidator::class])
@MustBeDocumented
annotation class ValidISODateString(
    val message: String =
        "The provided date is invalid. Expected format is ${ISODateString.REGEX}, and date must exist.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ISODateStringValidator : ConstraintValidator<ValidISODateString, String> {
    override fun isValid(
        value: String?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || ISODateString(value).isValid()
    }
}

data class ISODateString(val value: String) {

    companion object {
        const val REGEX = "^\\d{4}-\\d{2}-\\d{2}\$"
    }

    fun isValid(): Boolean {
        return try {
            LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE)
            true
        } catch (e: DateTimeParseException) {
            false
        }
    }
}
