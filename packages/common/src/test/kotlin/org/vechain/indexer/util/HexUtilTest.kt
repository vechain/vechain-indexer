package org.vechain.indexer.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.vechain.indexer.utils.HexUtil
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class HexUtilTest {

    @ParameterizedTest
    @CsvSource(
        "0x, false",
        "0x0, true",
        "8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, true",
        "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, true",
        "Hello World!!, false",
        "xxxxxxxxxxxxxxxxxx, false",
        "hex, false",
    )
    fun `validate hex`(address: String, valid: Boolean) {
        expectThat(HexUtil.isValid(address)).isEqualTo(valid)
    }

    @ParameterizedTest
    @CsvSource(
        "123, 0x123",
        "0x123, 0x123",
    )
    fun `add prefix`(input: String, output: String) {
        expectThat(HexUtil.addPrefix(input)).isEqualTo(output)
    }

    @ParameterizedTest
    @CsvSource(
        "ABC, 0xabc",
        "0xabc, 0xabc",
        "0xAbC, 0xabc",
    )
    fun `normalise address`(input: String, output: String) {
        expectThat(HexUtil.normalise(input)).isEqualTo(output)
    }

}