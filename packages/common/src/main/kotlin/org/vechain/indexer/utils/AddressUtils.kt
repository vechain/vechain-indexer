package org.vechain.indexer.utils

import org.web3j.abi.FunctionReturnDecoder
import java.math.BigInteger

object AddressUtils {

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
        return BigInteger(HexUtils.removePrefix(address), 16)
    }

    fun decode(data: String): String {

        if (isValid(data))
            return data

        val address = FunctionReturnDecoder.decodeAddress(data)

        require(isValid(address)) { "Failed to decode address for data: $data" }

        return address
    }

}