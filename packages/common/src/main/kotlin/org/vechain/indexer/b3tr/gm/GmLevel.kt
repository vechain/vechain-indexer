package org.vechain.indexer.b3tr.gm

import java.math.BigInteger

enum class GMLevelName {
    ALL,
    EARTH,
    MOON,
    MERCURY,
    VENUS,
    MARS,
    JUPITER,
    SATURN,
    URANUS,
    NEPTUNE,
    GALAXY;

    companion object {
        fun map(bi: BigInteger): GMLevelName =
            when (bi) {
                BigInteger.ZERO -> ALL
                BigInteger.ONE -> EARTH
                BigInteger.TWO -> MOON
                BigInteger.valueOf(3) -> MERCURY
                BigInteger.valueOf(4) -> VENUS
                BigInteger.valueOf(5) -> MARS
                BigInteger.valueOf(6) -> JUPITER
                BigInteger.valueOf(7) -> SATURN
                BigInteger.valueOf(8) -> URANUS
                BigInteger.valueOf(9) -> NEPTUNE
                BigInteger.valueOf(10) -> GALAXY
                else -> throw IllegalArgumentException("Invalid GM level: $bi")
            }
    }
}
