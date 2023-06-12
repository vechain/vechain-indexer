package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.model.rest.ContractType
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [ContractTypeValidator::class])
@MustBeDocumented
annotation class ValidContractType(
    val message: String = "Invalid contract type parameter",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class ContractTypeValidator : ConstraintValidator<ValidContractType, String> {
    override fun isValid(type: String?, constraintValidatorContext: ConstraintValidatorContext): Boolean {
        return type == null || ContractType.byNameIgnoreCaseOrNull(type) != null
    }
}