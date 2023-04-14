package org.vechain.indexer.utils

import org.vechain.indexer.constants.MASTER_EVENT_SIGNATURE
import org.vechain.indexer.constants.TRANSFER_EVENT_SIGNATURE
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.specifications.ContractSpecification

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

    fun isContractType(specification: ContractSpecification, rawData: String?): Boolean {
        rawData ?: return false
        return specification.functions.all { rawData.contains(it) } &&
                specification.events.all { rawData.contains(it) }
    }
}