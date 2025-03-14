package org.vechain.indexer.utils

import org.vechain.devkit.Function
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.specifications.ContractSpecification
import org.vechain.indexer.thor.model.Clause
import org.web3j.abi.EventEncoder
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

object ContractUtils {
    private fun rawDataContains(
        value: String,
        rawData: String,
    ): Boolean {
        val cleansedValue =
            if (value.startsWith("00")) {
                value.trimStart('0')
            } else {
                value
            }

        return rawData.lowercase().contains(cleansedValue.lowercase())
    }

    fun isContractType(
        specification: ContractSpecification,
        rawData: String,
    ): Boolean =
        specification.functions.all { rawDataContains(value = it, rawData = rawData) } &&
            specification.events.all { rawDataContains(value = it, rawData = rawData) }

    /** @param methodSignature - Example: "baz(uint32,bool)" */
    fun getFunctionSignature(methodSignature: String): String {
        val input = methodSignature.toByteArray()
        val hash = Hash.sha3(input)
        return HexUtils.removePrefix(Numeric.toHexString(hash).substring(0, 10))
    }

    /** @param canonicalName - Example: "Deposit(address,hash256,uint256)" */
    fun getEventSignature(canonicalName: String): String =
        HexUtils.removePrefix(EventEncoder.buildEventSignature(canonicalName))

    fun createClause(
        address: String,
        function: FunctionDefinition,
        vararg args: Any,
    ): Clause {
        val func = Function(JsonUtils.mapper.writeValueAsString(function))
        val encoded = func.encodeToHex(true, *args)
        return Clause(to = address, data = encoded, value = "0x0")
    }
}
