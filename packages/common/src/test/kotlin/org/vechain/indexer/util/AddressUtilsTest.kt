package org.vechain.indexer.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.vechain.indexer.utils.AddressUtils
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class AddressUtilsTest {

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
        expectThat(AddressUtils.isValid(address)).isEqualTo(valid)
    }

    @ParameterizedTest
    @CsvSource(
        "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, false",
        "0x8d66DA6448, true",
        "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa, false",
    )
    fun `check not valid address`(address: String, notValid: Boolean) {
        expectThat(AddressUtils.isNotValid(address)).isEqualTo(notValid)
    }

}