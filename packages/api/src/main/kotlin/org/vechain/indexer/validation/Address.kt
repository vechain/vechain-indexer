package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.utils.AddressUtil
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AddressValidator::class])
@MustBeDocumented
annotation class Address(
    val message: String = "The provided address is invalid. It must match ${AddressUtil.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class AddressValidator : ConstraintValidator<Address, String> {
    override fun isValid(value: String, constraintValidatorContext: ConstraintValidatorContext): Boolean {
        return AddressUtil.isValid(value)
    }
}