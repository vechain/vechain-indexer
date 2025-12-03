package org.vechain.indexer.validator.domain

import java.math.BigInteger
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.model.abi.InputOutput
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.VTHO_CONTRACT_ADDRESS
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.models.DecodedValidatorInfo

/**
 * Handles decoding of validator data from Thor smart contract calls. All ABI-related logic is
 * centralized here.
 */
object ValidatorDecoder {
    // --- Public API ---

    /** Decode all validator info in one go from inspection responses. */
    fun decodeResponseInfo(
        responses: List<InspectionResult>,
        validatorsAbi: Map<String, AbiElement>,
    ): DecodedValidatorInfo? {
        if (responses.size < 2 || !responses[0].hasAbiData() || !responses[1].hasAbiData()) {
            return null
        }

        val decodedValidators =
            FunctionReturnDecoder.decode(
                responses[0].data,
                validatorsAbi["getValidators"]!!.outputs,
            )

        val totalWeight = decodeSingle(responses, validatorsAbi, 1, "totalStake", "totalWeight")
        val vthoTotalSupply =
            decodeSingle(responses, validatorsAbi, 2, "vthoTotalSupply", "vthoTotalSupply")
        val vetPriceUsd = decodeSingle(responses, validatorsAbi, 3, "getVetPriceUsd", "vetPriceUsd")
        val vthoPriceUsd =
            decodeSingle(responses, validatorsAbi, 4, "getVthoPriceUsd", "vthoPriceUsd")
        val vthoBurned = decodeSingle(responses, validatorsAbi, 5, "totalBurned", "totalBurned")

        return DecodedValidatorInfo(
            decodedValidators,
            totalWeight,
            vthoTotalSupply,
            vetPriceUsd,
            vthoPriceUsd,
            vthoBurned,
        )
    }

    /** Decode raw validator list from ABI output. */
    fun decodeValidators(
        responses: List<InspectionResult>,
        validatorsAbi: AbiElement,
    ): Map<String, Any?> = FunctionReturnDecoder.decode(responses[0].data, validatorsAbi.outputs)

    /** Resolve total VTHO issued = totalSupply + burned. */
    fun decodeVTHOIssued(responses: List<InspectionResult>): BigInteger {
        if (responses.size < 2 || !responses[0].hasAbiData() || !responses[1].hasAbiData()) {
            return BigInteger.ZERO
        }

        val decodedTotalSupply =
            FunctionReturnDecoder.decode(
                responses[0].data,
                listOf(InputOutput("uint256", "vthoTotalSupply", "uint256")),
            )

        val totalSupply = decodedTotalSupply["vthoTotalSupply"] as? BigInteger ?: BigInteger.ZERO

        return totalSupply
    }

    /** ABI clauses for fetching validator contract data. */
    fun buildClauses(getAllValidatorInfoSC: String): List<Clause> {
        val abiFunctions =
            listOf(
                // getValidators
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
                ),
                // totalStake
                FunctionDefinition(
                    name = "totalStake",
                    inputs = emptyList(),
                    outputs =
                        listOf(
                            FunctionParameter("totalStake", "uint256"),
                            FunctionParameter("totalWeight", "uint256"),
                        ),
                    stateMutability = "view",
                ),
                // vthoTotalSupply
                FunctionDefinition(
                    name = "vthoTotalSupply",
                    inputs = emptyList(),
                    outputs = listOf(FunctionParameter("vthoTotalSupply", "uint256")),
                    stateMutability = "view",
                ),
                // getVetPriceUsd
                FunctionDefinition(
                    name = "getVetPriceUsd",
                    inputs = emptyList(),
                    outputs = listOf(FunctionParameter("vetPriceUsd", "uint128")),
                    stateMutability = "view",
                ),
                // getVthoPriceUsd
                FunctionDefinition(
                    name = "getVthoPriceUsd",
                    inputs = emptyList(),
                    outputs = listOf(FunctionParameter("vthoPriceUsd", "uint128")),
                    stateMutability = "view",
                ),
                // totalBurned
                FunctionDefinition(
                    name = "totalBurned",
                    inputs = emptyList(),
                    outputs = listOf(FunctionParameter("totalBurned", "uint256")),
                    stateMutability = "view",
                ),
            )

        return abiFunctions.map { fn -> ContractUtils.createClause(getAllValidatorInfoSC, fn) }
    }

    /** ABI clauses for fetching VTHO totals (supply + burned). */
    fun buildVTHOTotalsClauses(): List<Clause> =
        listOf(
            ContractUtils.createClause(
                VTHO_CONTRACT_ADDRESS,
                FunctionDefinition(
                    name = "totalSupply",
                    inputs = emptyList(),
                    outputs = listOf(FunctionParameter("vthoTotalSupply", "uint256")),
                    stateMutability = "view",
                ),
            ),
            ContractUtils.createClause(
                VTHO_CONTRACT_ADDRESS,
                FunctionDefinition(
                    name = "totalBurned",
                    inputs = emptyList(),
                    outputs = listOf(FunctionParameter("vthoBurned", "uint256")),
                    stateMutability = "view",
                ),
            ),
        )

    fun getValidatorPeriodDetails(
        validatorIds: List<String>,
        responses: List<InspectionResult>,
        validatorsAbi: Map<String, AbiElement>,
    ): Map<String, Pair<Long, Long>>? {
        val decodedResponse = decodeResponseInfo(responses, validatorsAbi) ?: return null

        val ids = decodedResponse.decodedValidators.listOf<String>("masters")
        val stakingPeriodLengths =
            decodedResponse.decodedValidators.listOf<BigInteger>("stakingPeriodLengths")
        val startBlocks = decodedResponse.decodedValidators.listOf<BigInteger>("startBlocks")

        val periodDetails = mutableMapOf<String, Pair<Long, Long>>()
        ids.forEachIndexed { index, id ->
            if (validatorIds.contains(id)) {
                val startBlock = startBlocks[index].toLong()
                val stakingPeriodLength = stakingPeriodLengths[index].toLong()
                periodDetails[id] = Pair(startBlock, stakingPeriodLength)
            }
        }

        return periodDetails
    }

    fun DecodedValidatorInfo.hasDelegations(address: String): Int {
        val ids = this.decodedValidators.listOf<String>("masters")
        val delegatorsStake = this.decodedValidators.listOf<BigInteger>("delegatorsStake")
        val statuses = this.decodedValidators.listOf<BigInteger>("statuses")
        val index = ids.indexOf(address)
        if (index == -1) return -1
        val status = statuses[index].toInt()
        if (status != 2) return -1

        return if (delegatorsStake[index] > BigInteger.ZERO) {
            1
        } else {
            0
        }
    }

    // --- Private helpers ---

    private fun decodeSingle(
        responses: List<InspectionResult>,
        abi: Map<String, AbiElement>,
        index: Int,
        functionName: String,
        key: String,
    ): BigInteger {
        val decoded =
            FunctionReturnDecoder.decode(
                responses[index].data,
                abi[functionName]?.outputs
                    ?: throw IllegalArgumentException("ABI not found for $functionName"),
            )
        return decoded[key] as? BigInteger
            ?: throw IllegalStateException("Expected BigInteger for $functionName.$key")
    }

    fun InspectionResult.hasAbiData(): Boolean = this.data.isNotBlank() && this.data != "0x"
}
