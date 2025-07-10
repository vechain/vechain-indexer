package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.model.stargate.TokenLevel

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TokenLevelValidator::class])
@MustBeDocumented
annotation class ValidTokenLevel(
    val message: String =
        "The provided level ID is invalid. Must be one of the TokenLevel enum values (case-insensitive).",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

// Make validator case-insensitive
class TokenLevelValidator : ConstraintValidator<ValidTokenLevel, String?> {
    override fun isValid(
        value: String?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || TokenLevel.fromString(value) != null
    }
}
