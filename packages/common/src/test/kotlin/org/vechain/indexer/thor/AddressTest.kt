package org.vechain.indexer.thor

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class AddressTest {

    @ParameterizedTest
    @CsvSource(
        "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, true",
        "0x8d66DA6448, false",
        "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855abc, false",
        "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855GG, false",
        "8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, true",
        "8D66DA6448C6144E894B7CD91FA1AE65310A4855, true",
        "0x8d66da6448c6144e894b7cd91fa1ae65310a4855, true",
    )
    fun `check valid address`(address: String, valid: Boolean) {
        expectThat(Address(address).isValid()).isEqualTo(valid)
    }

    @ParameterizedTest
    @CsvSource(
        "0x, false",
        "0X0000000000000000000000000000000000000000, false",
        "0x0000000000000000, false",
        "0x0000000000000000000000000000000000000000, true",
    )
    fun `check zero address`(address: String, isZero: Boolean) {
        expectThat(Address(address).isZero()).isEqualTo(isZero)
    }
}
