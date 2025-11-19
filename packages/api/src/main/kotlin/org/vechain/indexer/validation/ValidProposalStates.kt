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
        "Invalid proposal states provided. Must be a comma-separated list of valid ProposalState enum values (case-insensitive).",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

// Validator for comma-separated proposal states
class ProposalStatesValidator : ConstraintValidator<ValidProposalStates, String?> {
    override fun isValid(
        value: String?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        if (value == null || value.isBlank()) {
            return true // null or empty is valid (optional parameter)
        }

        return value.split(",").map { it.trim() }.all { state -> isValidProposalState(state) }
    }

    private fun isValidProposalState(state: String): Boolean {
        return ProposalState.entries.any { it.name.equals(state, ignoreCase = true) }
    }
}
