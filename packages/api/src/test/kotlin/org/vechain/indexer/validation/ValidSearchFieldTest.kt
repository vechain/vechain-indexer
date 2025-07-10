package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidSearchFieldTest {

    private val validator = SearchFieldsValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts empty list`() {
        expect { that(validator.isValid(emptyList(), DummyContext())).isTrue() }
    }

    @Test
    fun `accepts valid search fields`() {
        val validFields = listOf("to", "from", "origin", "gasPayer")
        expect {
            that(validator.isValid(validFields, DummyContext())).isTrue()
            that(validator.isValid(listOf("to", "from"), DummyContext())).isTrue()
        }
    }

    @Test
    fun `rejects invalid search fields`() {
        expect {
            that(validator.isValid(listOf("to", "invalidField"), DummyContext())).isFalse()
            that(validator.isValid(listOf("foo"), DummyContext())).isFalse()
        }
    }
}
