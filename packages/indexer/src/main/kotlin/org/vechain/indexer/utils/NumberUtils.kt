package org.vechain.indexer.utils

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object NumberUtils {
    // Commonly used scaling factors
    private val WEI_DECIMALS = BigDecimal.TEN.pow(18) // 1e18

    /**
     * Convert from wei (BigInteger) to VET (BigDecimal). Example: 1000000000000000000 wei => 1.0
     * VET
     */
    fun toVET(value: BigInteger, scale: Int = 2): BigDecimal =
        value.toBigDecimal().divide(WEI_DECIMALS, scale, RoundingMode.HALF_UP)

    /**
     * Convert from raw BigInteger to percentage of a total. Example: validatorWeight /
     * totalWeight * 100
     */
    fun toPercentageOf(value: BigInteger, total: BigInteger, scale: Int = 4): BigDecimal =
        if (total == BigInteger.ZERO) {
            BigDecimal.ZERO
        } else {
            value
                .toBigDecimal()
                .multiply(BigDecimal(100))
                .divide(total.toBigDecimal(), scale, RoundingMode.HALF_UP)
        }

    /** Convert from raw BigInteger to a normalized probability (0–1). */
    fun toProbabilityOf(value: BigInteger, total: BigInteger, scale: Int = 6): BigDecimal =
        if (total == BigInteger.ZERO) {
            BigDecimal.ZERO
        } else {
            value.toBigDecimal().divide(total.toBigDecimal(), scale, RoundingMode.HALF_UP)
        }
}
