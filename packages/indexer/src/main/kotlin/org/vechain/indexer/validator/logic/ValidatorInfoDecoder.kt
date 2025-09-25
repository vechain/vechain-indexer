package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.rest.ExecuteCodeResponse
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.utils.NumberUtils.toSafeDecimal128
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator

object ValidatorInfoDecoder {
    /** Get the latest information and stats for each validator */
    fun getLatestValidatorInfo(
        responses: List<ExecuteCodeResponse>,
        validatorsAbi: MutableMap<String, AbiElement>,
        existingDocs: Map<String, Validator>,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): ValidatorCycleContext {
        val decodedInfo = decodeResponseInfo(responses, validatorsAbi)

        val (validators, remove) =
            unpackValidators(
                decodedInfo.decodedValidators,
                existingDocs,
                decodedInfo.totalWeight,
                decodedInfo.vthoTotalSupply,
                decodedInfo.vetPriceUsd,
                decodedInfo.vthoPriceUsd,
                blockId,
                blockNumber,
                blockTimestamp,
            )

        val validatorMap = validators.associateBy { it.id }.toMutableMap()
        remove.forEach { validatorMap.remove(it) }

        return ValidatorCycleContext(
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            _validators = validatorMap,
        )
    }

    /** Unpack information on chain and use to update exiting documents */
    fun unpackValidators(
        decoded: Map<String, Any?>,
        existingDocs: Map<String, Validator>,
        totalWeight: BigInteger,
        totalVTHOSupply: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        retentionPeriod: Long = 777_600,
    ): Pair<List<Validator>, List<String>> {
        val ids = decoded.listOf<String>("masters")
        val endorsers = decoded.listOf<String>("endorsors")
        val statuses = decoded.listOf<BigInteger>("statuses")
        val onlines = decoded.listOf<Boolean>("onlines")
        val offlineBlocks = decoded.listOf<BigInteger>("offlineBlocks")
        val stakingPeriodLengths = decoded.listOf<Int>("stakingPeriodLengths")
        val startBlocks = decoded.listOf<BigInteger>("startBlocks")
        val exitBlocks = decoded.listOf<BigInteger>("exitBlocks")
        val completedPeriods = decoded.listOf<BigInteger>("completedPeriods")
        val lockedVET = decoded.listOf<BigInteger>("validatorLockedStakes")
        val lockedWeight = decoded.listOf<BigInteger>("validatorLockedWeights")
        val delegatorsStake = decoded.listOf<BigInteger>("delegatorsStake")
        val queuedStake = decoded.listOf<BigInteger>("totalQueuedStakes")
        val exitingStake = decoded.listOf<BigInteger>("totalExitingStakes")

        val rows =
            ids.indices.map { i ->
                DecodedValidatorRow(
                    id = ids[i],
                    endorser = endorsers[i],
                    status = statuses[i],
                    online = onlines[i],
                    offlineBlock = offlineBlocks[i],
                    stakingPeriodLength = stakingPeriodLengths[i],
                    startBlock = startBlocks[i],
                    exitBlock = exitBlocks[i],
                    completedPeriods = completedPeriods[i],
                    validatorLockedVET = lockedVET[i],
                    validatorLockedWeight = lockedWeight[i],
                    delegatorsStake = delegatorsStake[i],
                    totalQueuedStake = queuedStake[i],
                    totalExitingStake = exitingStake[i],
                )
            }

        val active =
            rows.mapNotNull { row ->
                buildValidator(
                    row,
                    existingDocs[row.id],
                    totalWeight,
                    totalVTHOSupply,
                    vetPriceUsd,
                    vthoPriceUsd,
                    blockId,
                    blockNumber,
                    blockTimestamp,
                    retentionPeriod,
                )
            }

        val disappeared =
            existingDocs.keys.minus(ids.toSet()).mapNotNull { id ->
                val oldVal = existingDocs[id]
                if (oldVal != null && oldVal.status != Status.EXITED) {
                    oldVal.copy(
                        status = Status.EXITED,
                        blockNumber = blockNumber,
                        blockTimestamp = blockTimestamp,
                    )
                } else {
                    null
                }
            }

        val toDelete =
            existingDocs.values
                .filter { v -> (v.blockNumber + retentionPeriod) < blockNumber }
                .map { it.id }

        return active + disappeared to toDelete
    }

    /** Create Validator using latest on chain info and calculations */
    fun buildValidator(
        row: DecodedValidatorRow,
        existingDoc: Validator?,
        totalWeight: BigInteger,
        totalVTHOSupply: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        retentionPeriod: Long = 777600, // 3 Months
    ): Validator? {
        val exitBlock = row.exitBlock.toLong()
        if ((exitBlock + retentionPeriod) < blockNumber) return null

        val vetPrice = ValidatorCalculations.toUsdPrice(vetPriceUsd)
        val vthoPrice = ValidatorCalculations.toUsdPrice(vthoPriceUsd)

        val validatorVET = NumberUtils.toVET(row.validatorLockedVET)
        val delegatorVET = NumberUtils.toVET(row.delegatorsStake)
        val totalVET = validatorVET + delegatorVET

        val queuedStake = NumberUtils.toVET(row.totalQueuedStake)
        val exitingStake = NumberUtils.toVET(row.totalExitingStake)

        val validatorTvl = validatorVET * vetPrice
        val delegatorTvl = delegatorVET * vetPrice
        val totalTvl = validatorTvl + delegatorTvl

        val vthoSupply = NumberUtils.toVET(totalVTHOSupply)

        val blocksOffline =
            ValidatorCalculations.calculateOfflineBlocks(
                existingDoc?.offlineBlocks,
                row.online,
                row.offlineBlock.toLong(),
                blockNumber,
            )

        val percentageOffline =
            ValidatorCalculations.calculatePercentageOffline(
                blocksOffline,
                row.startBlock.toLong(),
                blockNumber,
            )

        val prevTotalVTHOSupply = existingDoc?.totalVTHOSupply?.bigDecimalValue() ?: vthoSupply
        val blockProbability = NumberUtils.toProbabilityOf(row.validatorLockedWeight, totalWeight)
        val blocksPerYear = ValidatorCalculations.blocksPerYear(blockProbability)

        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            ValidatorCalculations.calculateValidatorYield(
                validatorTvl,
                delegatorTvl,
                row.delegatorsStake > BigInteger.ZERO,
                blocksPerYear,
                vthoSupply,
                prevTotalVTHOSupply,
                vthoPrice,
            )

        val status = ValidatorCalculations.resolveStatus(row.exitBlock, row.status)

        return Validator(
            id = row.id,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            endorser = row.endorser,
            beneficiary = existingDoc?.beneficiary,
            status = Status.fromCode(status),
            online = row.online,
            offlineBlocks = blocksOffline,
            cyclePeriodLength = row.stakingPeriodLength.toLong(),
            startBlock = row.startBlock.toLong(),
            completedPeriods = row.completedPeriods.toLong(),
            vetStaked = toSafeDecimal128(totalVET),
            validatorVetStaked = toSafeDecimal128(validatorVET),
            delegatorVetStaked = toSafeDecimal128(delegatorVET),
            queuedVetStaked = toSafeDecimal128(queuedStake),
            exitingVetStaked = toSafeDecimal128(exitingStake),
            totalWeight = toSafeDecimal128(NumberUtils.toVET(row.validatorLockedWeight)),
            blockProbability = toSafeDecimal128(blockProbability),
            blocksPerEpoch = toSafeDecimal128(blockProbability * BigDecimal(180)),
            blocksPerYear = toSafeDecimal128(blocksPerYear),
            validatorTvl = toSafeDecimal128(validatorTvl),
            delegatorTvl = toSafeDecimal128(delegatorTvl),
            totalTvl = toSafeDecimal128(totalTvl),
            delegations = existingDoc?.delegations ?: emptyMap(),
            incomingDelegations = existingDoc?.incomingDelegations ?: emptyMap(),
            outgoingDelegations = existingDoc?.outgoingDelegations ?: emptyMap(),
            delegationInfo = existingDoc?.delegationInfo ?: emptyMap(),
            delegationsToBeActioned = existingDoc?.delegationsToBeActioned ?: emptyList(),
            validatorYield = toSafeDecimal128(validatorYield),
            tvlBasedYield = toSafeDecimal128(tvlBasedYield),
            avgDelegatorYield = toSafeDecimal128(avgDelegatorYield),
            totalVTHOSupply = toSafeDecimal128(vthoSupply),
            percentageOffline = toSafeDecimal128(percentageOffline),
            version = (existingDoc?.version ?: 0) + 1,
            cycleEndBlock =
                ValidatorCalculations.calculateNextCycleBlock(
                    row.startBlock,
                    row.completedPeriods,
                    row.stakingPeriodLength,
                ),
        )
    }

    /** Decode function call response */
    private fun decodeSingle(
        responses: List<ExecuteCodeResponse>,
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

    fun decodeResponseInfo(
        responses: List<ExecuteCodeResponse>,
        validatorsAbi: Map<String, AbiElement>,
    ): DecodedValidatorInfo {
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

        return DecodedValidatorInfo(
            decodedValidators,
            totalWeight,
            vthoTotalSupply,
            vetPriceUsd,
            vthoPriceUsd,
        )
    }

    data class DecodedValidatorInfo(
        val decodedValidators: Map<String, Any?>,
        val totalWeight: BigInteger,
        val vthoTotalSupply: BigInteger,
        val vetPriceUsd: BigInteger,
        val vthoPriceUsd: BigInteger,
    )

    data class DecodedValidatorRow(
        val id: String,
        val endorser: String,
        val status: BigInteger,
        val online: Boolean,
        val offlineBlock: BigInteger,
        val stakingPeriodLength: Int,
        val startBlock: BigInteger,
        val exitBlock: BigInteger,
        val completedPeriods: BigInteger,
        val validatorLockedVET: BigInteger,
        val validatorLockedWeight: BigInteger,
        val delegatorsStake: BigInteger,
        val totalQueuedStake: BigInteger,
        val totalExitingStake: BigInteger,
    )

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> Map<String, Any?>.listOf(key: String): List<T> =
        this[key] as? List<T>
            ?: throw IllegalArgumentException(
                "Expected List<${T::class.simpleName}> for key '$key'"
            )
}
