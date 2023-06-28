package org.vechain.indexer.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.vechain.indexer.model.Address
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
}
