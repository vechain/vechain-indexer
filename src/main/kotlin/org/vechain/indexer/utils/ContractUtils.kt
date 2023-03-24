package org.vechain.indexer.utils

import org.springframework.stereotype.Component
import org.vechain.indexer.constants.MASTER_EVENT_SIGNATURE
import org.vechain.indexer.constants.TRANSFER_EVENT_SIGNATURE
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.model.TxOutputs
import org.vechain.indexer.specifications.ContractSpecification

@Component
class ContractUtils {
    fun isContractDeployment(outputs: TxOutputs): Boolean {
        return outputs.events.isNotEmpty() && outputs.events[0].topics.isNotEmpty() && outputs.events[0].topics[0] == MASTER_EVENT_SIGNATURE
    }

    fun findTransferEvents(events: List<TxEvent>): List<TxEvent> {
        return events.filter { it.topics.size == 3 && it.topics[0] == TRANSFER_EVENT_SIGNATURE }
    }

    fun removeTopicPadding(topic: String): String {
        if (topic.length <= 26) return topic
        return "0x${topic.substring(26)}"
    }

    fun isContractType(specification: ContractSpecification, rawData: String): Boolean {
        return specification.functions.all { rawData.contains(it) } &&
                specification.events.all { rawData.contains(it) }
    }
}