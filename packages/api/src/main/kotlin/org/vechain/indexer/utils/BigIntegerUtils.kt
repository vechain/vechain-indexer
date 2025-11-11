package org.vechain.indexer.utils

import java.math.BigInteger

object BigIntegerUtils {
    fun fromHexOrDecimal(value: String): BigInteger {
        if (value.startsWith("0x")) return BigInteger(value.substring(2), 16)
        if (value.contains(Regex("[a-fA-F]+"))) return BigInteger(value, 16)
        return BigInteger(value)
    }
}
