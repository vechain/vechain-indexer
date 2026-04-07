package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.stargate.StargateTokenHistoryService

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [StargateTokenHistoryEventNameValidator::class])
@MustBeDocumented
annotation class ValidStargateTokenHistoryEventName(
    val message: String =
        "Invalid Stargate token event name. Allowed values are: STARGATE_DELEGATE_LEGACY, STARGATE_CLAIM_REWARDS_BASE_LEGACY, STARGATE_CLAIM_REWARDS_DELEGATE_LEGACY, STARGATE_UNDELEGATE_LEGACY, STARGATE_STAKE, STARGATE_UNSTAKE, STARGATE_DELEGATE_ACTIVE, STARGATE_DELEGATE_REQUEST, STARGATE_DELEGATE_EXIT_REQUEST, STARGATE_DELEGATION_EXITED_VALIDATOR, STARGATE_DELEGATION_EXITED, STARGATE_DELEGATE_REQUEST_CANCELLED, STARGATE_CLAIM_REWARDS, STARGATE_BOOST, STARGATE_MANAGER_ADDED, STARGATE_MANAGER_REMOVED, TRANSFER_NFT, NFT_SALE, VEVOTE_VOTE_CAST",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class StargateTokenHistoryEventNameValidator :
    ConstraintValidator<ValidStargateTokenHistoryEventName, List<String>?> {
    private val allowedValues =
        StargateTokenHistoryService.ALLOWED_EVENT_NAMES.map { it.name }.toSet()

    override fun isValid(value: List<String>?, context: ConstraintValidatorContext): Boolean {
        if (value == null || value.isEmpty()) {
            return true
        }

        return value.all { it in allowedValues }
    }
}
