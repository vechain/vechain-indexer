package org.vechain.indexer.utils

import org.springframework.stereotype.Component
import org.vechain.indexer.model.Clause
import org.vechain.indexer.model.TxOutputs

@Component
class ContractUtils {
    fun isContractDeployment(clause: Clause, output: TxOutputs): Boolean {
        return clause.to == null && clause.data != "0x" && output.contractAddress != null
    }
}