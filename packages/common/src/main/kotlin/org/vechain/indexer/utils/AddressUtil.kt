package org.vechain.indexer.utils

import org.web3j.abi.FunctionReturnDecoder
import java.math.BigInteger

object AddressUtil {

    /**
     * Optional prefix 0x
     */
    const val REGEX = "^(0x)?[0-9a-fA-F]{40}\$"

    fun isValid(address: String?): Boolean {
        return address?.matches(Regex(REGEX)) ?: false
    }

    fun isNotValid(address: String?): Boolean {
        return !isValid(address)
    }


    fun toBigInt(address: String): BigInteger {
        return BigInteger(HexUtil.removePrefix(address), 16)
    }

    fun decode(data: String): String {

        if (isValid(data))
            return data

        val address = FunctionReturnDecoder.decodeAddress(data)

        if (isNotValid(address))
            throw IllegalArgumentException("Failed to decode address for data: $data")

        return address
    }

}