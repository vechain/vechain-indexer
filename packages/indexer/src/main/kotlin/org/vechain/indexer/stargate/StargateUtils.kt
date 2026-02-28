package org.vechain.indexer.stargate

import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.generic.IndexedEvent
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.ParamUtils.getAsInt
import org.vechain.indexer.utils.ParamUtils.getAsString

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

/** Require and decode a valid `levelId` parameter, converting to [TokenLevel]. */
fun IndexedEvent.requireLevel(): TokenLevel {
    val id =
        this.params.getAsInt("levelId")
            ?: throw IllegalArgumentException("Missing levelId in event params")

    return TokenLevel.fromOrdinal(id) ?: throw IllegalArgumentException("Invalid levelId: $id")
}

/** Require and decode an `owner` address parameter. */
fun IndexedEvent.requireOwner(): String {
    return this.params.getAsString("owner")
        ?: throw IllegalArgumentException("Missing owner in event params")
}
