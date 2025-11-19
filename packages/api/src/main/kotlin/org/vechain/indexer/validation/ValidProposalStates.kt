package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.b3tr.proposal.ProposalState

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ProposalStatesValidator::class])
@MustBeDocumented
annotation class ValidProposalStates(
    val message: String =
        "Invalid proposal states provided. Must be a list of valid ProposalState enum values (case-insensitive).",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

// Validator for proposal states list
class ProposalStatesValidator : ConstraintValidator<ValidProposalStates, List<String>?> {
    override fun isValid(
        value: List<String>?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || value.isEmpty()) {
            return true // null or empty is valid (optional parameter)
        }

        return value.all { state -> isValidProposalState(state.trim()) }
    }

    private fun isValidProposalState(state: String): Boolean {
        return ProposalState.entries.any { it.name.equals(state, ignoreCase = true) }
    }
}
