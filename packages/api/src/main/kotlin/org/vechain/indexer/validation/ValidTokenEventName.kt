package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TokenEventNameValidator::class])
@MustBeDocumented
annotation class ValidTokenEventName(
    val message: String =
        "Invalid token event name. Allowed values are: STARGATE_DELEGATE_LEGACY, STARGATE_STAKE, STARGATE_DELEGATE_REQUEST, STARGATE_DELEGATE_ACTIVE, STARGATE_UNDELEGATE_LEGACY, STARGATE_DELEGATE_EXIT_REQUEST, STARGATE_DELEGATION_EXITED_VALIDATOR, STARGATE_DELEGATION_EXITED, STARGATE_CLAIM_REWARDS, STARGATE_BOOST, STARGATE_MANAGER_ADDED, STARGATE_MANAGER_REMOVED, VEVOTE_VOTE_CAST, NFT_SALE, TRANSFER_NFT, B3TR_UPGRADE_GM",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class TokenEventNameValidator : ConstraintValidator<ValidTokenEventName, List<String>?> {
    private val allowedValues =
        setOf(
            "STARGATE_DELEGATE_LEGACY",
            "STARGATE_STAKE",
            "STARGATE_DELEGATE_REQUEST",
            "STARGATE_DELEGATE_ACTIVE",
            "STARGATE_UNDELEGATE_LEGACY",
            "STARGATE_DELEGATE_EXIT_REQUEST",
            "STARGATE_DELEGATION_EXITED_VALIDATOR",
            "STARGATE_DELEGATION_EXITED",
            "STARGATE_CLAIM_REWARDS",
            "STARGATE_BOOST",
            "STARGATE_MANAGER_ADDED",
            "STARGATE_MANAGER_REMOVED",
            "VEVOTE_VOTE_CAST",
            "NFT_SALE",
            "TRANSFER_NFT",
            "B3TR_UPGRADE_GM",
        )

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.isEmpty()) {
            return true
        }

        return value.all { it in allowedValues }
    }
}
