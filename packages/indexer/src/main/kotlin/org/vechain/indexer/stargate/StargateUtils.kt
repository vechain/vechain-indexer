package org.vechain.indexer.stargate

import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils

object StargateUtils {
    val issuanceFunction =
        FunctionDefinition(
            name = "issuance",
            inputs = emptyList(),
            outputs = listOf(FunctionParameter("issued", "uint256")),
            stateMutability = "view",
        )

    fun buildIssuanceClause(stakerSC: String): List<Clause> =
        listOf(ContractUtils.createClause(stakerSC, issuanceFunction))
}
