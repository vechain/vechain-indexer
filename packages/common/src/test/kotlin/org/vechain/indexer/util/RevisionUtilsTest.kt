package org.vechain.indexer.util

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.vechain.indexer.utils.RevisionUtils
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class RevisionUtilsTest {

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
        "0, true",
        "1, true",
        "-1, false",
        "9999999999999999, true",
        "99.3, false",
        "best, true",
        "BEST, true",
        "BeSt, true",
        " BEST  , true",
        "finalized, true",
        "FINALIZED, true",
        "Finalized, true",
        " FINALIZED  , true",
        "anyotherstring, false",
    )
    fun `check valid revision`(revision: String, valid: Boolean) {
        expectThat(RevisionUtils.isValid(revision)).isEqualTo(valid)
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
        "0, true",
        "1, true",
        "-1, false",
        "9999999999999999, true",
        "99.3, false",
        "best, true",
        "BEST, true",
        "BeSt, true",
        " BEST  , true",
        "finalized, true",
        "FINALIZED, true",
        "Finalized, true",
        " FINALIZED  , true",
        "anyotherstring, false",
    )
    fun `check not valid revision`(revision: String, valid: Boolean) {
        expectThat(RevisionUtils.isNotValid(revision)).isEqualTo(!valid)
    }
}
