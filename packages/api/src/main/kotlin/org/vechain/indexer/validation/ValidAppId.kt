package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.b3tr.AppId

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AppIdValidator::class])
@MustBeDocumented
annotation class ValidAppId(
    val message: String = "The provided appId is invalid. It must match ${AppId.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class AppIdValidator : ConstraintValidator<ValidAppId, AppId> {
    override fun isValid(
        value: AppId?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || value.isValid()
    }
}
