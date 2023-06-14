package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import org.vechain.indexer.utils.RevisionUtils
import kotlin.reflect.KClass

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [RevisionValidator::class])
@MustBeDocumented
annotation class Revision(
    val message: String = "The provided revision is invalid. It must be a positive integer, a valid block ID, 'best' or 'finalized'",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = []
)

class RevisionValidator : ConstraintValidator<Revision, String> {
    override fun isValid(value: String, constraintValidatorContext: ConstraintValidatorContext): Boolean {
        return RevisionUtils.isValid(value)
    }
}