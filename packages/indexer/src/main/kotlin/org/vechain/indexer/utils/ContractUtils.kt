package org.vechain.indexer.utils

import org.vechain.indexer.constants.MASTER_EVENT_SIGNATURE
import org.vechain.indexer.constants.TRANSFER_EVENT_SIGNATURE
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.specifications.ContractSpecification
import org.web3j.abi.EventEncoder
import org.web3j.crypto.Hash
import org.web3j.utils.Numeric

object ContractUtils {
    fun isMasterEvent(event: TxEvent): Boolean {
        return event.topics.isNotEmpty() && event.topics[0] == MASTER_EVENT_SIGNATURE
    }

    /**
     * NFTs length of topics is 4, FUNGIBLE is 3
     */
    fun findTransferEvents(events: List<TxEvent>): List<TxEvent> {
        return events.filter {
            (it.topics.size == 3 || it.topics.size == 4) &&
                    it.topics[0] == TRANSFER_EVENT_SIGNATURE
        }
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
        return HexUtil.removePrefix(
            Numeric.toHexString(hash).substring(0, 10)
        )
    }

    /**
     * @param canonicalName - Example: "Deposit(address,hash256,uint256)"
     */
    fun getEventSignature(canonicalName: String): String {
        return HexUtil.removePrefix(
            EventEncoder.buildEventSignature(canonicalName)
        )
    }
}