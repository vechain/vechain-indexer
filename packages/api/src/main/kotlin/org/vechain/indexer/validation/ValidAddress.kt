package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.model.Address

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AddressValidator::class])
@MustBeDocumented
annotation class ValidAddress(
    val message: String = "The provided address is invalid. It must match ${Address.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class AddressValidator : ConstraintValidator<ValidAddress, Address> {
    override fun isValid(
        value: Address?,
        constraintValidatorContext: ConstraintValidatorContext
    ): Boolean {
        return value == null || value.isValid()
    }
}
