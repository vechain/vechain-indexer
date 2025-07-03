package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [LevelIdValidator::class])
@MustBeDocumented
annotation class ValidLevelId(
    val message: String = "The provided level ID is invalid. Must be a number between 0 and 9 ",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class LevelIdValidator : ConstraintValidator<ValidLevelId, Int?> {
    override fun isValid(
        value: Int?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || value in 1..10
    }
}
