package org.vechain.indexer.utils

import org.springframework.stereotype.Component
import org.vechain.indexer.constants.TRANSFER_EVENT_SIGNATURE
import org.vechain.indexer.model.Clause
import org.vechain.indexer.model.TxEvent
import org.vechain.indexer.model.TxOutputs

@Component
class ContractUtils {
    fun isContractDeployment(clause: Clause, output: TxOutputs): Boolean {
        return clause.to == null && clause.data != "0x" && output.contractAddress != null
    }

    fun findTransferEvents(events: List<TxEvent>): List<TxEvent> {
        return events.filter { it.topics.size == 3 && it.topics[0] == TRANSFER_EVENT_SIGNATURE }
    }
}