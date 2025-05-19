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
    val message: String =
        "One of the provided addresses is invalid (must match ${Address.REGEX}) or the number of addresses exceeds the limit of 20",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class AddressListValidator : ConstraintValidator<ValidAddressList, List<Address>> {
    private val limit: Int = 20

    override fun isValid(
        value: List<Address>?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || (value.size <= limit && value.all { it.isValid() && !it.isZero() })
    }
}
