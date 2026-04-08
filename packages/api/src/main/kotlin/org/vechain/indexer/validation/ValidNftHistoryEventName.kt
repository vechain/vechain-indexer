package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.nft.NftHistoryService

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NftHistoryEventNameValidator::class])
@MustBeDocumented
annotation class ValidNftHistoryEventName(
    val message: String =
        "Invalid NFT history event name. Allowed values are: TRANSFER_NFT, NFT_SALE",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NftHistoryEventNameValidator : ConstraintValidator<ValidNftHistoryEventName, List<String>?> {
    private val allowedValues = NftHistoryService.ALLOWED_EVENT_NAMES.map { it.name }.toSet()

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.isEmpty()) {
            return true
        }

        return value.all { it in allowedValues }
    }
}
