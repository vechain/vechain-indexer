package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.transfer.TransferEventType

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TransferEventTypeValidator::class])
@MustBeDocumented
annotation class ValidTransferEventType(
    val message: String =
        "Invalid eventType value. Allowed values: VET, FUNGIBLE_TOKEN, NFT, SEMI_FUNGIBLE_TOKEN",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class TransferEventTypeValidator : ConstraintValidator<ValidTransferEventType, List<String>?> {
    private val allowedValues = TransferEventType.entries.map { it.name }.toSet()

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value.isNullOrEmpty()) {
            return true
        }

        return value.all { it in allowedValues }
    }
}
