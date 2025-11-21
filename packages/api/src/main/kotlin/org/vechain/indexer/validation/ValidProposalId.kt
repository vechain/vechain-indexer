package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.proposal.ProposalId

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ProposalIdValidator::class])
@MustBeDocumented
annotation class ValidProposalId(
    val message: String = "The provided proposalId is invalid. It must match ${ProposalId.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class ProposalIdValidator : ConstraintValidator<ValidProposalId, ProposalId> {
    override fun isValid(
        value: ProposalId?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        return value == null || value.isValid()
    }
}
