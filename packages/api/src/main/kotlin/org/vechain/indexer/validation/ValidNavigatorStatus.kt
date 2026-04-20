package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass
import org.vechain.indexer.b3tr.navigator.NavigatorStatus

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [NavigatorStatusValidator::class])
@MustBeDocumented
annotation class ValidNavigatorStatus(
    val message: String = "Invalid status value. Allowed values: ACTIVE, EXITING, DEACTIVATED.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class NavigatorStatusValidator : ConstraintValidator<ValidNavigatorStatus, List<String>?> {
    override fun isValid(
        value: List<String>?,
        constraintValidatorContext: ConstraintValidatorContext,
    ): Boolean {
        if (value.isNullOrEmpty()) {
            return true
        }

        return value.all { status ->
            NavigatorStatus.entries.any { it.name.equals(status.trim(), ignoreCase = true) }
        }
    }
}
