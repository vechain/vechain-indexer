package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.utils.AddressUtil
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [OptionalAddressesValidator::class])
@MustBeDocumented
annotation class OptionalAddresses(
    val message: String = "The provided addresses are invalid. They must match ${AddressUtil.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class OptionalAddressesValidator : ConstraintValidator<OptionalAddresses, List<String>?> {
    override fun isValid(value: List<String>?, context: ConstraintValidatorContext?): Boolean {
        return value.isNullOrEmpty() || value.all { AddressUtil.isValid(it) }
    }
}