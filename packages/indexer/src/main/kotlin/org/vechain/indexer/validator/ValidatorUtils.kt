package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.vechain.indexer.contracts.abi.FunctionDefinition
import org.vechain.indexer.contracts.abi.FunctionParameter
import org.vechain.indexer.event.model.abi.AbiElement
import org.vechain.indexer.event.utils.FunctionReturnDecoder
import org.vechain.indexer.thor.model.Clause
import org.vechain.indexer.thor.model.InspectionResult
import org.vechain.indexer.utils.ContractUtils
import org.vechain.indexer.utils.NumberUtils

object ValidatorUtils {
    private val SCALE = BigDecimal("1000000000000") // 1e12
    private val MAX_UINT32 = BigInteger.valueOf(4294967295L)

    /** Get the latest information and stats for each validator */
    fun getLatestValidatorInfo(
        responses: List<InspectionResult>,
        validatorsAbi: MutableMap<String, AbiElement>,
        existingDocs: Map<String, Validator>,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
    ): List<Validator> {
        val decodedInfo = decodeResponseInfo(responses, validatorsAbi)

        return unpackValidators(
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
    ): List<Validator> {
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

        return active + disappeared
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

        val vetPrice = toUsdPrice(vetPriceUsd)
        val vthoPrice = toUsdPrice(vthoPriceUsd)

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
            calculateOfflineBlocks(
                existingDoc?.offlineBlocks,
                row.online,
                row.offlineBlock.toLong(),
                blockNumber,
            )

        val percentageOffline =
            calculatePercentageOffline(blocksOffline, row.startBlock.toLong(), blockNumber)

        val prevTotalVTHOSupply = existingDoc?.totalVTHOSupply?.bigDecimalValue() ?: vthoSupply
        val blockProbability = NumberUtils.toProbabilityOf(row.validatorLockedWeight, totalWeight)
        val blocksPerYear = blocksPerYear(blockProbability)

        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            calculateValidatorYield(
                validatorTvl,
                delegatorTvl,
                row.delegatorsStake > BigInteger.ZERO,
                blocksPerYear,
                vthoSupply,
                prevTotalVTHOSupply,
                vthoPrice,
            )

        val status = resolveStatus(row.exitBlock, row.status)

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
            vetStaked = NumberUtils.toSafeDecimal128(totalVET),
            validatorVetStaked = NumberUtils.toSafeDecimal128(validatorVET),
            delegatorVetStaked = NumberUtils.toSafeDecimal128(delegatorVET),
            queuedVetStaked = NumberUtils.toSafeDecimal128(queuedStake),
            exitingVetStaked = NumberUtils.toSafeDecimal128(exitingStake),
            totalWeight =
                NumberUtils.toSafeDecimal128(NumberUtils.toVET(row.validatorLockedWeight)),
            blockProbability = NumberUtils.toSafeDecimal128(blockProbability),
            blocksPerEpoch = NumberUtils.toSafeDecimal128(blockProbability * BigDecimal(180)),
            blocksPerYear = NumberUtils.toSafeDecimal128(blocksPerYear),
            validatorTvl = NumberUtils.toSafeDecimal128(validatorTvl),
            delegatorTvl = NumberUtils.toSafeDecimal128(delegatorTvl),
            totalTvl = NumberUtils.toSafeDecimal128(totalTvl),
            validatorYield = NumberUtils.toSafeDecimal128(validatorYield),
            tvlBasedYield = NumberUtils.toSafeDecimal128(tvlBasedYield),
            avgDelegatorYield = NumberUtils.toSafeDecimal128(avgDelegatorYield),
            totalVTHOSupply = NumberUtils.toSafeDecimal128(vthoSupply),
            percentageOffline = NumberUtils.toSafeDecimal128(percentageOffline),
            version = (existingDoc?.version ?: 0) + 1,
            cycleEndBlock =
                calculateNextCycleBlock(
                    row.startBlock,
                    row.completedPeriods,
                    row.stakingPeriodLength,
                ),
        )
    }

    /** Decode function call response */
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

    fun decodeResponseInfo(
        responses: List<InspectionResult>,
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

    fun decodeValidators(
        responses: List<InspectionResult>,
        validatorsAbi: AbiElement,
    ): Map<String, Any?> = FunctionReturnDecoder.decode(responses[0].data, validatorsAbi.outputs)

    /** Convert oracle price (BigInteger) into USD BigDecimal */
    fun toUsdPrice(value: BigInteger): BigDecimal =
        BigDecimal(value).divide(SCALE, 12, RoundingMode.HALF_UP)

    /** Work out how many blocks a validator has been offline */
    fun calculateOfflineBlocks(
        previousOffline: Long?,
        online: Boolean,
        offlineStart: Long,
        currentBlock: Long,
    ): Long {
        if (online) return previousOffline ?: 0L

        return if ((previousOffline ?: 0L) == 0L) {
            currentBlock - offlineStart
        } else {
            (previousOffline ?: 0L) + 1
        }
    }

    /** Percentage offline across total blocks */
    fun calculatePercentageOffline(
        blocksOffline: Long,
        startBlock: Long,
        currentBlock: Long,
    ): BigDecimal {
        val totalBlocks = currentBlock - startBlock
        if (totalBlocks <= 0) return BigDecimal.ZERO

        return BigDecimal(blocksOffline)
            .divide(BigDecimal(totalBlocks), 6, RoundingMode.HALF_UP)
            .multiply(BigDecimal(100))
    }

    /** Expected blocks per year given block probability */
    fun blocksPerYear(blockProbability: BigDecimal): BigDecimal {
        // 360 * 24 * 365.25 is average blocks per year on VeChain
        return BigDecimal.valueOf(360 * 24 * 365.25).multiply(blockProbability)
    }

    /** Determine validator status (normal vs exiting) */
    fun resolveStatus(exitBlock: BigInteger, rawStatus: BigInteger): Int {
        return if (exitBlock == MAX_UINT32) rawStatus.toInt() else 4 // 4 = Exiting
    }

    /** Calculate next cycle start block */
    fun calculateNextCycleBlock(
        startBlock: BigInteger,
        completedPeriods: BigInteger,
        stakingPeriodLength: Int,
    ): Long =
        startBlock.toLong() + ((completedPeriods.toLong() + 1L) * stakingPeriodLength.toLong())

    /**
     * Calculate validator yields:
     * - validatorYield: yield going to the validator
     * - tvlBasedYield: yield relative to TVL
     * - avgDelegatorYield: yield going to delegators
     */
    fun calculateValidatorYield(
        validatorTvl: BigDecimal,
        delegatorTvl: BigDecimal,
        hasDelegations: Boolean,
        blocksPerYear: BigDecimal,
        totalVTHOSupply: BigDecimal,
        prevTotalVTHOSupply: BigDecimal,
        vthoPrice: BigDecimal,
    ): Triple<BigDecimal, BigDecimal, BigDecimal> {
        // Issuance in USD
        val issuance = totalVTHOSupply.subtract(prevTotalVTHOSupply).max(BigDecimal.ZERO)
        val issuanceUsd = issuance.multiply(vthoPrice)
        val annualIssuanceUsd = blocksPerYear.multiply(issuanceUsd)

        val totalTvl = validatorTvl.add(delegatorTvl)
        if (totalTvl.compareTo(BigDecimal.ZERO) == 0) {
            return Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
        }

        val validatorYield =
            if (validatorTvl > BigDecimal.ZERO) {
                if (hasDelegations) {
                    annualIssuanceUsd
                        .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal("0.3"))
                        .multiply(BigDecimal(100))
                } else {
                    annualIssuanceUsd
                        .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                }
            } else {
                BigDecimal.ZERO
            }

        val validatorTvlRatio =
            if (totalTvl > BigDecimal.ZERO) {
                validatorTvl.divide(totalTvl, 12, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

        val tvlBasedYield =
            if (validatorTvl > BigDecimal.ZERO) {
                if (hasDelegations) {
                    annualIssuanceUsd
                        .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                        .multiply(validatorTvlRatio)
                        .multiply(BigDecimal(100))
                } else {
                    annualIssuanceUsd
                        .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                }
            } else {
                BigDecimal.ZERO
            }

        val avgDelegatorYield =
            if (hasDelegations && delegatorTvl > BigDecimal.ZERO) {
                annualIssuanceUsd
                    .divide(delegatorTvl, 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("0.7"))
                    .multiply(BigDecimal(100))
            } else {
                BigDecimal.ZERO
            }

        return Triple(validatorYield, tvlBasedYield, avgDelegatorYield)
    }

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
            )

        return abiFunctions.map { fn -> ContractUtils.createClause(getAllValidatorInfoSC, fn) }
    }

    fun computeNextCycleStart(snapshot: ValidatorSnapshot, currentBlock: Long): Long {
        val offset = currentBlock - snapshot.startBlock
        val positionInCycle = offset % snapshot.stakingPeriodLength
        val currentCycleStart = currentBlock - positionInCycle
        return currentCycleStart + snapshot.stakingPeriodLength
    }

    fun InspectionResult.hasAbiData(): Boolean =
        this.data != null && this.data.isNotBlank() && this.data != "0x"

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
    inline fun <reified T> Map<String, Any?>.listOf(key: String): List<T> =
        this[key] as? List<T>
            ?: throw IllegalArgumentException(
                "Expected List<${T::class.simpleName}> for key '$key'"
            )
}
