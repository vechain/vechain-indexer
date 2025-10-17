package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.thor.Address
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidAddressTest {

    private val validator = AddressValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts valid address`() {
        val address = Address("0x" + "1".repeat(40))
        expect { that(validator.isValid(address, DummyContext())).isTrue() }
    }

    @Test
    fun `rejects invalid address`() {
        val address = Address("invalid")
        expect { that(validator.isValid(address, DummyContext())).isFalse() }
    }

    @Test
    fun `rejects zero address`() {
        val address = Address("0x" + "0".repeat(40))
        expect { that(validator.isValid(address, DummyContext())).isFalse() }
    }
}
