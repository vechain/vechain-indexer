package org.vechain.indexer.utils

import java.math.BigInteger
import java.math.RoundingMode
import kotlin.toBigDecimal

fun scaleDown(value: BigInteger, scale: Int): Double {
    return value
        .toBigDecimal()
        .divide(BigInteger.TEN.pow(scale).toBigDecimal(), scale, RoundingMode.HALF_UP)
        .toDouble()
}
