package org.vechain.indexer.validator

import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.event.AbiLoader
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.rest.ExecuteCodeResponse
import org.vechain.indexer.thor.AddressUtils
import org.vechain.indexer.thor.ThorService
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.validator.domain.ValidatorDecoder.decodeValidators
import org.vechain.indexer.validator.domain.ValidatorDecoder.hasAbiData
import org.vechain.indexer.validator.logic.ValidatorAssembler.listOf
import org.vechain.indexer.validator.logic.ValidatorCalculator.calculateNextCycleStart

@Profile("validator", "delegation", "stargate", "stargate-token")
@Service
class ValidatorDelegationService(
    private val thorService: ThorService,
    @param:Value("\${business-event.substitutions.BUILTIN_STAKER_CONTRACT}")
    private val stakerSC: String,
) {
    private val cachedGetDelegationAbi: ConcurrentHashMap<String, AbiElement> = ConcurrentHashMap()

    /**
     * Returns the next status of a validator.
     * - If the current status is [Status.EXITING], the next status will be [Status.EXITED].
     * - Otherwise, the validator is considered [Status.ACTIVE].
     *
     * @param status Current validator status.
     * @return The next status.
     */
    fun nextStatus(status: Status): Status =
        if (status == Status.EXITING) Status.EXITED else Status.ACTIVE

    /**
     * Fetches a validator’s validation period information directly from chain.
     *
     * Uses `getValidationPeriodDetails` to fetch the validator’s start block and period length, and
     * calculates the next cycle start block relative to the current block.
     *
     * @param validatorId Validator identifier (e.g., address).
     * @param currentBlock The current block number.
     * @return Pair of (periodLength, nextCycleStart). If the validator has not started, returns
     *   (periodLength, 0).
     */
    fun getValidatorPeriodInfo(validatorId: String, currentBlock: Long): Pair<Long, Long> {
        val clause =
            ContractUtils.createClause(
                stakerSC,
                getDelegationsAbiFunctions("getValidationPeriodDetails"),
                AddressUtils.toBigInt(validatorId),
            )
        val response = thorService.executeReadOnlyCode(listOf(clause))
        val (startBlock, periodLength) = decodeValidationPeriodDetails(response[0].data)

        if (startBlock == 0L) return periodLength to 0L

        val offset = currentBlock - startBlock
        val positionInCycle = offset % periodLength
        val currentCycleStart = currentBlock - positionInCycle
        val nextCycleStart = currentCycleStart + periodLength

        return periodLength to nextCycleStart
    }

    /**
     * Extracts the start block from a chain response.
     *
     * @param response The chain execution response.
     * @return The decoded start block.
     */
    fun determineStartBlock(response: ExecuteCodeResponse): Long =
        decodeValidationPeriodDetails(response.data).first

    fun resolveCycleInfo(
        validatorId: String,
        blockNumber: Long,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): Pair<Long, Long> {
        val snapshot = validatorSnapshots[validatorId]
        return if (snapshot != null) {
            snapshot.stakingPeriodLength to calculateNextCycleStart(snapshot, blockNumber)
        } else {
            getValidatorPeriodInfo(validatorId, blockNumber)
        }
    }

    /**
     * Resolves the exit block for a validator.
     *
     * Uses cached snapshot if available, otherwise queries the chain.
     *
     * @param validatorId The validator identifier.
     * @param validatorSnapshots Cached validator snapshots.
     * @return The exit block number.
     */
    fun getValidatorExitBlock(
        validatorId: String,
        validatorSnapshots: Map<String, ValidatorSnapshot>,
    ): Long =
        validatorSnapshots[validatorId]?.exitBlock
            ?: run {
                val clause =
                    ContractUtils.createClause(
                        stakerSC,
                        getDelegationsAbiFunctions("getValidationPeriodDetails"),
                        AddressUtils.toBigInt(validatorId),
                    )
                val response = thorService.executeReadOnlyCode(listOf(clause))
                val decoded =
                    FunctionReturnDecoder.decode(
                        response[0].data,
                        getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
                    )
                (decoded["exitBlock"] as BigInteger).toLong()
            }

    /**
     * Decodes validator snapshots from chain responses.
     *
     * Parses masters, staking period lengths, start blocks, and exit blocks.
     *
     * @param callResponses Chain responses from the staking contract.
     * @return Map of validatorId → [ValidatorSnapshot].
     */
    fun decodeValidatorSnapshots(
        callResponses: List<InspectionResult>
    ): Map<String, ValidatorSnapshot> {
        val usable = callResponses.filter { it.hasAbiData() }
        if (usable.isEmpty()) {
            // nothing returned from the chain for this block; treat as no validators
            return emptyMap()
        }

        val decoded = decodeValidators(callResponses, getDelegationsAbiFunctions("getValidators"))
        val masters = decoded.listOf<String>("masters")
        val periods = decoded.listOf<BigInteger>("stakingPeriodLengths")
        val starts = decoded.listOf<BigInteger>("startBlocks")
        val exits = decoded.listOf<BigInteger>("exitBlocks")

        return masters
            .mapIndexed { i, id ->
                id to
                    ValidatorSnapshot(
                        validatorId = id,
                        stakingPeriodLength = periods[i].toLong(),
                        startBlock = starts[i].toLong(),
                        exitBlock = exits[i].toLong(),
                    )
            }
            .toMap()
    }

    /**
     * Fetches validation period details for a list of validators.
     *
     * Performs a batch chain call for efficiency.
     *
     * @param validatorIds List of validator identifiers.
     * @return List of [ExecuteCodeResponse] with ABI-encoded results.
     */
    fun fetchValidationPeriodDetails(validatorIds: List<String>): List<ExecuteCodeResponse> {
        if (validatorIds.isEmpty()) return emptyList()

        val clauses =
            validatorIds.map { validatorId ->
                ContractUtils.createClause(
                    stakerSC,
                    getDelegationsAbiFunctions("getValidationPeriodDetails"),
                    AddressUtils.toBigInt(validatorId),
                )
            }
        return thorService.executeReadOnlyCode(clauses)
    }

    /**
     * Calculates the next cycle block for a validator.
     * - If the last cycle end is in the future, that value is returned.
     * - Otherwise, aligns to the next multiple of the cycle length after the current block.
     *
     * @param lastCycleEnd The last known cycle end block (nullable).
     * @param cycleLength Length of a single cycle.
     * @param currentBlock The current block number.
     * @return The block number where the next cycle starts.
     */
    fun resolveNextCycleBlock(lastCycleEnd: Long?, cycleLength: Long, currentBlock: Long): Long {
        val base = lastCycleEnd ?: currentBlock
        return if (base > currentBlock) {
            base
        } else {
            base + ((currentBlock - base) / cycleLength + 1) * cycleLength
        }
    }

    /** Decodes `getValidationPeriodDetails` response. */
    private fun decodeValidationPeriodDetails(data: String): Pair<Long, Long> {
        val decoded =
            FunctionReturnDecoder.decode(
                data,
                getDelegationsAbiFunctions("getValidationPeriodDetails").outputs,
            )
        val startBlock = (decoded["startBlock"] as BigInteger).toLong()
        val periodLength = (decoded["period"] as BigInteger).toLong()
        return startBlock to periodLength
    }

    /** Retrieves and caches ABI function definitions for delegation-related calls. */
    private fun getDelegationsAbiFunctions(name: String): AbiElement =
        cachedGetDelegationAbi[name]
            ?: run {
                val abis = AbiLoader.loadFunctions("abis/stargate", listOf(name))
                val abi =
                    abis.firstOrNull { it.name == name }
                        ?: throw IllegalArgumentException("Function '$name' not found in ABI")
                cachedGetDelegationAbi[name] = abi
                abi
            }
}
