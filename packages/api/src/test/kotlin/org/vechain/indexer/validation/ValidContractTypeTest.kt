package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.model.rest.ContractType
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidContractTypeTest {

    private val validator = ContractTypeValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts valid contract type values (case-insensitive)`() {
        ContractType.entries.forEach { type ->
            expect {
                that(validator.isValid(type.name.lowercase(), DummyContext())).isTrue()
                that(validator.isValid(type.name.uppercase(), DummyContext())).isTrue()
            }
        }
    }

    @Test
    fun `rejects invalid contract type value`() {
        expect {
            that(validator.isValid("not_a_type", DummyContext())).isFalse()
            that(validator.isValid("", DummyContext())).isFalse()
        }
    }
}
