package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.utils.TransactionUtils
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TransactionIdValidator::class])
@MustBeDocumented
annotation class TransactionId(
    val message: String = "The provided transaction ID is invalid. It must match ${TransactionUtils.REGEX}",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class TransactionIdValidator : ConstraintValidator<TransactionId, String> {
    override fun isValid(value: String, constraintValidatorContext: ConstraintValidatorContext): Boolean {
        return TransactionUtils.isIdValid(value)
    }
}