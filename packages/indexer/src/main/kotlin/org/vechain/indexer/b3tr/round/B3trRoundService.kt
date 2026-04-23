package org.vechain.indexer.b3tr.round

import java.math.BigInteger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.client.ThorClient
import org.vechain.indexer.thor.model.BlockRevision
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils

@Service
open class B3trRoundService(
    private val thorClient: ThorClient,
    @param:Value("\${business-event.substitutions.EMISSIONS}")
    private val emissionsContractAddress: String,
) {
    private val getCurrentCycleAbi: AbiElement by lazy {
        AbiLoader.loadFunctions("abis/b3tr", listOf("getCurrentCycle")).firstOrNull {
            it.name == "getCurrentCycle"
        } ?: throw IllegalArgumentException("Function 'getCurrentCycle' not found in ABI")
    }

    open suspend fun getCurrentRound(blockId: String): Int? =
        getCurrentRound(BlockRevision.Id(blockId))

    open suspend fun getCurrentRound(revision: BlockRevision): Int? {
        val clause = ContractUtils.createClause(emissionsContractAddress, getCurrentCycleAbi)
        val response = thorClient.inspectClauses(listOf(clause), revision).first()
        return parseCurrentRound(response)
    }

    internal fun parseCurrentRound(response: InspectionResult): Int? {
        if (response.reverted) {
            return null
        }

        val decoded = FunctionReturnDecoder.decode(response.data, getCurrentCycleAbi.outputs)
        val currentRound = decoded[""] as? BigInteger ?: return null
        return currentRound.toInt()
    }
}
