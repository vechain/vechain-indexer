package org.vechain.indexer.utils

import org.vechain.indexer.model.ExecuteCodeResponse
import org.web3j.abi.FunctionReturnDecoder
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.AbiTypes
import org.web3j.abi.datatypes.Type
import org.web3j.abi.datatypes.Utf8String

object TransactionUtils {
    fun isSuccessWithData(res: ExecuteCodeResponse): Boolean {
        return res.reverted == false &&
                res.vmError.isNullOrEmpty() &&
                res.data != null &&
                res.data != "0x"
    }
    

    /**
     * Extracts the revert reason from the `data` of a transaction response.
     * Returns null if there is no revert reason.
     */
    fun getRevertReason(data: String): String? {
        // Numeric.toHexString(Hash.sha3("Error(string)".getBytes())).substring(0, 10)
        val errorMethodId = "0x08c379a0"

        if (!data.startsWith(errorMethodId)) return null

        val revertReasonTypes: List<TypeReference<Type<*>>> =
            listOf(
                TypeReference.create(AbiTypes.getType("string") as Class<Type<*>>)
            )

        val encodedRevertReason: String = data.substring(errorMethodId.length)
        val decoded: List<Type<*>> = FunctionReturnDecoder.decode(encodedRevertReason, revertReasonTypes)
        val decodedRevertReason: Utf8String = decoded[0] as Utf8String
        return decodedRevertReason.value
    }
}