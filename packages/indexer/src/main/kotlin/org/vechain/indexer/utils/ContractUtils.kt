package org.vechain.indexer.utils

import org.vechain.indexer.contracts.specifications.ContractSpecification
import org.vechain.thor.model.TxEvent
import org.web3j.abi.EventEncoder
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

object ContractUtils {

    private const val MASTER_EVENT_SIGNATURE = "0xb35bf4274d4295009f1ec66ed3f579db287889444366c03d3a695539372e8951"

    fun isMasterEvent(event: TxEvent): Boolean {
        return event.topics.isNotEmpty() && event.topics[0] == MASTER_EVENT_SIGNATURE
    }


    private fun rawDataContains(value: String, rawData: String): Boolean {
        val cleansedValue = if (value.startsWith("00")) {
            value.trimStart('0')
        } else value

        return rawData.lowercase()
            .contains(cleansedValue.lowercase())
    }

    fun isContractType(specification: ContractSpecification, rawData: String): Boolean {
        return specification.functions.all { rawDataContains(value = it, rawData = rawData) } &&
                specification.events.all { rawDataContains(value = it, rawData = rawData) }
    }

    /**
     * @param methodSignature - Example: "baz(uint32,bool)"
     */
    fun getFunctionSignature(methodSignature: String): String {
        val input = methodSignature.toByteArray()
        val hash = Hash.sha3(input)
        return HexUtils.removePrefix(
            Numeric.toHexString(hash).substring(0, 10)
        )
    }

    /**
     * @param canonicalName - Example: "Deposit(address,hash256,uint256)"
     */
    fun getEventSignature(canonicalName: String): String {
        return HexUtils.removePrefix(
            EventEncoder.buildEventSignature(canonicalName)
        )
    }
}