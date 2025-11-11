package org.vechain.indexer.validation

import jakarta.validation.Constraint
import jakarta.validation.ConstraintValidator
import jakarta.validation.ConstraintValidatorContext
import jakarta.validation.Payload
import kotlin.reflect.KClass

private val TOKEN_ID_REGEX = Regex("^(0x)?[A-Fa-f0-9]+$")

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
@Constraint(validatedBy = [TokenIdValidator::class])
@MustBeDocumented
annotation class ValidTokenId(
    val message: String = "tokenId must contain only hex or decimal characters.",
    val groups: Array<KClass<*>> = [],
    val payload: Array<KClass<out Payload>> = [],
)

class TokenIdValidator : ConstraintValidator<ValidTokenId, String> {
    override fun isValid(value: String?, context: ConstraintValidatorContext): Boolean {
        return value.isNullOrBlank() || TOKEN_ID_REGEX.matches(value)
    }
}
