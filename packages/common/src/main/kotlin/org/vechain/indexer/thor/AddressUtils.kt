package org.vechain.indexer.thor

import java.math.BigInteger
import org.web3j.abi.FunctionReturnDecoder

object AddressUtils {

    fun toBigInt(address: String): BigInteger {
        return BigInteger(HexUtils.removePrefix(address), 16)
    }

    fun decode(data: String): String {

        if (Address(data).isValid()) return data

        val address = FunctionReturnDecoder.decodeAddress(data)

        require(Address(address).isValid()) { "Failed to decode address for data: $data" }

        return address
    }
}
