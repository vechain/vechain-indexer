package org.vechain.indexer.utils

import java.math.BigInteger

object AddressUtil {

    /**
     * Optional prefix 0x
     */
    const val REGEX = "^(0x)?[0-9a-fA-F]{40}\$"

    fun isValid(address: String): Boolean {
        return address.matches(Regex(REGEX))
    }

    fun isNotValid(address: String): Boolean {
        return !isValid(address)
    }


    fun toBigInt(address: String): BigInteger {
        return BigInteger(HexUtil.removePrefix(address), 16)
    }

}