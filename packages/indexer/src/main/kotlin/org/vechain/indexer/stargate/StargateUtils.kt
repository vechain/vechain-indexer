package org.vechain.indexer.stargate

import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils

object StargateUtils {
    val balanceOfFunction =
        // balanceOf
        FunctionDefinition(
            name = "balanceOf",
            inputs = listOf(FunctionParameter("owner", "address", emptyList(), "address")),
            outputs = listOf(FunctionParameter("balance", "uint256")),
            stateMutability = "view",
        )

    fun buildBalanceOfClause(stargateSC: String): List<Clause> =
        listOf(
            ContractUtils.createClause(
                "0x0000000000000000000000000000456e65726779",
                balanceOfFunction,
                AddressUtils.toBigInt(stargateSC),
            )
        )
}
