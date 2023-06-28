package org.vechain.indexer.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.vechain.indexer.utils.HexUtils
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class HexUtilsTest {

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
        expectThat(HexUtils.isValid(address)).isEqualTo(valid)
    }

    @ParameterizedTest
    @CsvSource(
        "123, 0x123",
        "0x123, 0x123",
    )
    fun `add prefix`(input: String, output: String) {
        expectThat(HexUtils.addPrefix(input)).isEqualTo(output)
    }

    @ParameterizedTest
    @CsvSource(
        "ABC, 0xabc",
        "0xabc, 0xabc",
        "0xAbC, 0xabc",
    )
    fun `normalise address`(input: String, output: String) {
        expectThat(HexUtils.normalise(input)).isEqualTo(output)
    }

    @ParameterizedTest
    @CsvSource(
        "0x, false",
        "0x0, false",
        "8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, false",
        "0x8d66DA6448c6144E894B7cD91Fa1Ae65310A4855, false",
        "0x00ed92e829394e540850997728b8ae07fd54a9f1767feefdd730c9c44ffda3df, true",
        "0x00ed92e829394e540850997728b8ae07fd54a9f1767feefdd730C9c44ffda3DF, true",
        "0x00ed94a506afa94c96f01e9f477e51e6e6435e2524a1d9d07f3d8262eafe9532, true",
        "0x00ed94a506afa94c96f01e9f477e51e6e6435e2524a1d9d07f3d8262EAFE9532, true",
        "Hello World!!, false",
        "xxxxxxxxxxxxxxxxxx, false",
        "hex, false",
    )
    fun `validate block id`(address: String, valid: Boolean) {
        expectThat(HexUtils.isValidBlockID(address)).isEqualTo(valid)
    }
}
