package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

class OptionalAddressesTest {
    val validator = OptionalAddressesValidator()

    @Test
    fun `empty list should be true`() {
        expect {
            that(validator.isValid(emptyList(), null)).isTrue()
        }
    }

    @Test
    fun `null list should be true`() {
        expect {
            that(validator.isValid(null, null)).isTrue()
        }
    }

    @Test
    fun `list with valid addresses should be true`() {
        expect {
            that(validator.isValid(listOf("0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"), null)).isTrue()
        }
    }

    @Test
    fun `list with invalid address should be false`() {
        expect {
            that(validator.isValid(listOf("0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa", "badAddress"), null)).isFalse()
        }
    }
}