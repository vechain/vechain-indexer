package org.vechain.indexer.validator.domain

import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils

/**
 * Residual helpers retained for non-validator indexers (Stargate token, History,
 * VthoGeneratedByBlock) that still rely on the legacy `GetAllValidators` aggregator call data. The
 * V1 ValidatorIndexer that owned this file has been deleted; the surviving pieces are kept here
 * (rather than inlined into each caller) because porting those other pipelines off the aggregator
 * is a separate, out-of-scope refactor.
 */
object ValidatorDecoder {
    /**
     * Returns the `getValidators` clause against the legacy aggregator at [getAllValidatorInfoSC].
     * Existing callers index into `[0]` / `first()` — return type kept as `List<Clause>` so those
     * call sites compile unchanged.
     */
    fun buildClauses(getAllValidatorInfoSC: String): List<Clause> {
        val getValidators =
            FunctionDefinition(
                name = "getValidators",
                inputs = emptyList(),
                outputs =
                    listOf(
                        FunctionParameter("masters", "address[]"),
                        FunctionParameter("endorsors", "address[]"),
                        FunctionParameter("statuses", "uint8[]"),
                        FunctionParameter("onlines", "bool[]"),
                        FunctionParameter("offlineBlocks", "uint32[]"),
                        FunctionParameter("stakingPeriodLengths", "uint32[]"),
                        FunctionParameter("startBlocks", "uint32[]"),
                        FunctionParameter("exitBlocks", "uint32[]"),
                        FunctionParameter("completedPeriods", "uint32[]"),
                        FunctionParameter("validatorLockedStakes", "uint256[]"),
                        FunctionParameter("validatorLockedWeights", "uint256[]"),
                        FunctionParameter("delegatorsStake", "uint256[]"),
                        FunctionParameter("validatorQueuedStakes", "uint256[]"),
                        FunctionParameter("totalQueuedStakes", "uint256[]"),
                        FunctionParameter("totalExitingStakes", "uint256[]"),
                        FunctionParameter("totalNextPeriodWeights", "uint256[]"),
                        FunctionParameter("nextPeriodDelegationStakes", "uint256[]"),
                    ),
                stateMutability = "view",
            )
        return listOf(ContractUtils.createClause(getAllValidatorInfoSC, getValidators))
    }

    fun InspectionResult.hasAbiData(): Boolean = this.data.isNotBlank() && this.data != "0x"

    /** Decodes the first response in [responses] using [abi]'s output schema. */
    fun decodeValidators(responses: List<InspectionResult>, abi: AbiElement): Map<String, Any?> {
        if (responses.isEmpty() || !responses[0].hasAbiData()) return emptyMap()
        return FunctionReturnDecoder.decode(responses[0].data, abi.outputs)
    }

    @Suppress("UNCHECKED_CAST")
    inline fun <reified T> Map<String, Any?>.listOf(key: String): List<T> =
        this[key] as? List<T>
            ?: throw IllegalArgumentException(
                "Expected List<${T::class.simpleName}> for key '$key'"
            )
}
