package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.utils.AddressUtils
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AddressNullableValidator::class])
@MustBeDocumented
annotation class AddressNullable(
    val message: String = "The provided address is invalid. It must match ${AddressUtils.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class AddressNullableValidator : ConstraintValidator<AddressNullable, String> {
    override fun isValid(value: String?, constraintValidatorContext: ConstraintValidatorContext): Boolean {
        return value.isNullOrEmpty() || AddressUtils.isValid(value)
    }
}