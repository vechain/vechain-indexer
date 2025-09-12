package org.vechain.indexer.validation

import jakarta.validation.ConstraintValidatorContext
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class ValidISODateStringTest {

    @Test
    fun `valid ISO dates pass isValid`() {
        assertTrue(ISODateString("2025-09-10").isValid())
        assertTrue(ISODateString("2000-01-01").isValid())
    }

    @Test
    fun `invalid format fails isValid`() {
        assertFalse(ISODateString("2025/09/10").isValid())
        assertFalse(ISODateString("10-09-2025").isValid())
        assertFalse(ISODateString("2025-9-1").isValid())
        assertFalse(ISODateString("").isValid())
    }

    @Test
    fun `non-existent dates fail isValid`() {
        assertFalse(ISODateString("2025-02-30").isValid())
        assertFalse(ISODateString("2025-04-31").isValid())
    }

    @Test
    fun `leap year handling`() {
        assertTrue(ISODateString("2024-02-29").isValid())
        assertFalse(ISODateString("2023-02-29").isValid())
    }

    @Test
    fun `validator returns true for null`() {
        val validator = ISODateStringValidator()
        val ctx = mock(ConstraintValidatorContext::class.java)
        assertTrue(validator.isValid(null, ctx))
    }

    @Test
    fun `validator delegates to ISODateString`() {
        val validator = ISODateStringValidator()
        val ctx = mock(ConstraintValidatorContext::class.java)
        assertTrue(validator.isValid("2025-09-10", ctx))
        assertFalse(validator.isValid("2025-02-30", ctx))
        assertFalse(validator.isValid("", ctx))
    }
}
