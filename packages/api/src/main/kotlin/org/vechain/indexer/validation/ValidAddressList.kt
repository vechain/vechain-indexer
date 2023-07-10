package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.model.Address

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [AddressListValidator::class])
@MustBeDocumented
annotation class ValidAddressList(
    val message: String = "The provided address is invalid. It must match ${Address.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class AddressListValidator : ConstraintValidator<ValidAddressList, List<Address>> {
    private val limit: Int = 20

    override fun isValid(
        value: List<Address>?,
        constraintValidatorContext: ConstraintValidatorContext
    ): Boolean {
        return value == null || value.size <= limit || value.all { it.isValid() }
    }
}
