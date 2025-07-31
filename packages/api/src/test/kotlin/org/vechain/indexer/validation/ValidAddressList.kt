package org.vechain.indexer.validation

import org.junit.jupiter.api.Test
import org.vechain.indexer.thor.Address
import strikt.api.expect
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ValidAddressListTest {

    private val validator = AddressListValidator()

    @Test
    fun `accepts null value`() {
        expect { that(validator.isValid(null, DummyContext())).isTrue() }
    }

    @Test
    fun `accepts valid address list`() {
        val addresses = List(5) { Address("0x" + "1".repeat(40)) }
        expect { that(validator.isValid(addresses, DummyContext())).isTrue() }
    }

    @Test
    fun `rejects list with invalid address`() {
        val addresses = listOf(Address("0x" + "1".repeat(40)), Address("invalid"))
        expect { that(validator.isValid(addresses, DummyContext())).isFalse() }
    }

    @Test
    fun `rejects list with zero address`() {
        val addresses = listOf(Address("0x" + "0".repeat(40)))
        expect { that(validator.isValid(addresses, DummyContext())).isFalse() }
    }

    @Test
    fun `rejects list exceeding limit`() {
        val addresses = List(21) { Address("0x" + "1".repeat(40)) }
        expect { that(validator.isValid(addresses, DummyContext())).isFalse() }
    }
}
