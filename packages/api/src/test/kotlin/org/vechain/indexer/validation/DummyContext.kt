package org.vechain.indexer.validation

import jakarta.validation.ConstraintValidatorContext

class DummyContext : ConstraintValidatorContext {
    override fun getDefaultConstraintMessageTemplate() = ""

    override fun disableDefaultConstraintViolation() {}

    override fun buildConstraintViolationWithTemplate(messageTemplate: String) =
        throw NotImplementedError()

    override fun <T : Any?> unwrap(p0: Class<T?>?): T? = throw NotImplementedError()

    override fun getClockProvider() = throw NotImplementedError()
}
