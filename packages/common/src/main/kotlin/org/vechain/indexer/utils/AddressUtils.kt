package org.vechain.indexer.utils

import org.vechain.indexer.model.Address
import org.web3j.abi.FunctionReturnDecoder
import java.math.BigInteger

object AddressUtils {

    fun toBigInt(address: String): BigInteger {
        return BigInteger(HexUtils.removePrefix(address), 16)
    }

    fun decode(data: String): String {

        if (Address(data).isValid())
            return data

        val address = FunctionReturnDecoder.decodeAddress(data)

        require(Address(address).isValid()) { "Failed to decode address for data: $data" }

        return address
    }

}