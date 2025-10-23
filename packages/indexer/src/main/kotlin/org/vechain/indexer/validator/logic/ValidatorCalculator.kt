package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.bson.types.Decimal128
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorSnapshot
import org.vechain.indexer.validator.models.DecodedValidatorRow

/** Performs all validator-related calculations (TVL, yields, offline %, next cycle, etc.) */
object ValidatorCalculator {
    private val BLOCKS_PER_YEAR = BigDecimal("3155760") // 360 * 24 * 365.25
    private val MAX_UINT32 = BigInteger.valueOf(4294967295L)
    private val MIN_VALIDATOR_STAKE = BigDecimal("25000000")

    /** Create Validator using latest on-chain info and calculations */
    fun buildValidator(
        row: DecodedValidatorRow,
        existingDoc: Validator?,
        totalWeight: BigInteger,
        vthoIssued: BigInteger,
        vetPriceUsd: BigInteger,
        vthoPriceUsd: BigInteger,
        blockId: String,
        blockNumber: Long,
        blockTimestamp: Long,
        nextPeriodTotalWeight: BigInteger,
    ): Validator {
        val vetPrice = NumberUtils.toVET(vetPriceUsd)
        val vthoPrice = NumberUtils.toVET(vthoPriceUsd)
        val vthoIssuedBD = NumberUtils.toVET(vthoIssued)

        val stakes = computeStakes(row, existingDoc, blockNumber)
        val tvl = computeTVL(stakes, vetPrice)
        val offline = computeOffline(existingDoc, row, blockNumber)
        val probabilities = computeProbabilities(row, totalWeight, nextPeriodTotalWeight)

        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            calculateValidatorYield(
                tvl.validatorTvl,
                tvl.delegatorTvl,
                row.delegatorsStake > BigInteger.ZERO,
                probabilities.blocksPerYear,
                vthoIssuedBD,
                vthoPrice,
            )

        val status = Status.fromCode(resolveStatus(row.exitBlock, row.status))

        val cycleEndBlock =
            calculateNextCycleBlock(row.startBlock, row.completedPeriods, row.stakingPeriodLength)

        val (nextCycleValidatorYield, nextCycleTvlBasedYield, nextCycleAvgDelegatorYield) =
            if (status == Status.EXITING && cycleEndBlock >= row.exitBlock.toLong()) {
                Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)
            } else {
                calculateValidatorYield(
                    stakes.nextCycleValidatorStake * vetPrice,
                    stakes.nextCycleDelegationStake * vetPrice,
                    stakes.nextCycleDelegationStake > BigDecimal.ZERO,
                    blocksPerYear(probabilities.blockProbabilityNextCycle),
                    vthoIssuedBD,
                    vthoPrice,
                )
            }

        return Validator(
            id = row.id,
            blockId = blockId,
            blockNumber = blockNumber,
            blockTimestamp = blockTimestamp,
            endorser = row.endorser,
            beneficiary = existingDoc?.beneficiary,
            status = status,
            online = row.online,
            offlineBlocks = offline.blocksOffline,
            cyclePeriodLength = row.stakingPeriodLength.toLong(),
            startBlock = row.startBlock.toLong(),
            completedPeriods = row.completedPeriods.toLong(),
            vetStaked = NumberUtils.toSafeDecimal128(stakes.totalVET),
            validatorVetStaked = NumberUtils.toSafeDecimal128(stakes.validatorVET),
            delegatorVetStaked = NumberUtils.toSafeDecimal128(stakes.delegatorVET),
            queuedVetStaked = NumberUtils.toSafeDecimal128(stakes.queuedStake),
            exitingVetStaked = NumberUtils.toSafeDecimal128(stakes.exitingStake),
            totalWeight =
                NumberUtils.toSafeDecimal128(NumberUtils.toVET(row.validatorLockedWeight)),
            blockProbability = NumberUtils.toSafeDecimal128(probabilities.blockProbability),
            blocksPerEpoch =
                NumberUtils.toSafeDecimal128(probabilities.blockProbability * BigDecimal(180)),
            blocksPerYear = NumberUtils.toSafeDecimal128(probabilities.blocksPerYear),
            validatorTvl = NumberUtils.toSafeDecimal128(tvl.validatorTvl),
            delegatorTvl = NumberUtils.toSafeDecimal128(tvl.delegatorTvl),
            totalTvl = NumberUtils.toSafeDecimal128(tvl.totalTvl),
            validatorYield = NumberUtils.toSafeDecimal128(validatorYield),
            tvlBasedYield = NumberUtils.toSafeDecimal128(tvlBasedYield),
            avgDelegatorYield = NumberUtils.toSafeDecimal128(avgDelegatorYield),
            nextCycleValidatorYield = NumberUtils.toSafeDecimal128(nextCycleValidatorYield),
            nextCycleTvlBasedYield = NumberUtils.toSafeDecimal128(nextCycleTvlBasedYield),
            nextCycleAvgDelegatorYield = NumberUtils.toSafeDecimal128(nextCycleAvgDelegatorYield),
            nftYieldsNextCycle =
                calculateNftLevelYields(
                    stakes.totalVET,
                    NumberUtils.toVET(row.nextPeriodDelegationStake),
                    NumberUtils.toVET(nextPeriodTotalWeight),
                    vthoIssuedBD,
                    vthoPrice,
                    vetPrice,
                    status,
                ),
            percentageOffline = NumberUtils.toSafeDecimal128(offline.percentageOffline),
            version = (existingDoc?.version ?: 0) + 1,
            cycleEndBlock = cycleEndBlock,
        )
    }

    // ------------------------------
    // Calculation helpers
    // ------------------------------

    data class Stakes(
        val validatorVET: BigDecimal,
        val delegatorVET: BigDecimal,
        val totalVET: BigDecimal,
        val queuedStake: BigDecimal,
        val exitingStake: BigDecimal,
        val nextCycleStake: BigDecimal,
        val nextCycleValidatorStake: BigDecimal,
        val nextCycleDelegationStake: BigDecimal,
    )

    data class Tvl(
        val validatorTvl: BigDecimal,
        val delegatorTvl: BigDecimal,
        val totalTvl: BigDecimal,
    )

    data class OfflineStats(val blocksOffline: Long, val percentageOffline: BigDecimal)

    data class Probabilities(
        val blockProbability: BigDecimal,
        val blockProbabilityNextCycle: BigDecimal,
        val blocksPerYear: BigDecimal,
    )

    private fun computeStakes(
        row: DecodedValidatorRow,
        existingDoc: Validator?,
        blockNumber: Long,
    ): Stakes {
        val validatorVET = NumberUtils.toVET(row.validatorLockedVET)
        val delegatorVET = NumberUtils.toVET(row.delegatorsStake)
        val totalVET = validatorVET + delegatorVET

        val queuedStake = NumberUtils.toVET(row.totalQueuedStake)
        val exitingStake = NumberUtils.toVET(row.totalExitingStake)
        val nextCycleStake = (queuedStake + totalVET - exitingStake).max(BigDecimal.ZERO)
        val queuedValidatorVetStaked = NumberUtils.toVET(row.validatorQueuedStake)
        val queuedDelegationVetStaked = queuedStake - queuedValidatorVetStaked

        val exitingValidatorVetStaked =
            if (existingDoc?.cycleEndBlock == blockNumber) {
                BigDecimal.ZERO
            } else {
                existingDoc?.exitingValidatorVetStaked ?: BigDecimal.ZERO
            }
        val exitingDelegationVetStaked = exitingStake - exitingValidatorVetStaked

        val nextCycleValidatorStake =
            validatorVET + queuedValidatorVetStaked - exitingValidatorVetStaked
        val nextCycleDelegationStake =
            delegatorVET + queuedDelegationVetStaked - exitingDelegationVetStaked

        return Stakes(
            validatorVET,
            delegatorVET,
            totalVET,
            queuedStake,
            exitingStake,
            nextCycleStake,
            nextCycleValidatorStake,
            nextCycleDelegationStake,
        )
    }

    private fun computeTVL(stakes: Stakes, vetPrice: BigDecimal): Tvl =
        Tvl(
            validatorTvl = stakes.validatorVET * vetPrice,
            delegatorTvl = stakes.delegatorVET * vetPrice,
            totalTvl = (stakes.validatorVET * vetPrice) + (stakes.delegatorVET * vetPrice),
        )

    private fun computeOffline(
        existingDoc: Validator?,
        row: DecodedValidatorRow,
        blockNumber: Long,
    ): OfflineStats {
        val blocksOffline =
            calculateOfflineBlocks(
                existingDoc?.offlineBlocks,
                row.online,
                row.offlineBlock.toLong(),
                blockNumber,
            )
        val percentageOffline =
            calculatePercentageOffline(blocksOffline, row.startBlock.toLong(), blockNumber)
        return OfflineStats(blocksOffline, percentageOffline)
    }

    private fun computeProbabilities(
        row: DecodedValidatorRow,
        totalWeight: BigInteger,
        nextPeriodTotalWeight: BigInteger,
    ): Probabilities {
        val blockProbability = NumberUtils.toProbabilityOf(row.validatorLockedWeight, totalWeight)
        val blockProbabilityNextCycle =
            NumberUtils.toProbabilityOf(row.totalNextPeriodWeight, nextPeriodTotalWeight)
        val blocksPerYear = blocksPerYear(blockProbability)
        return Probabilities(blockProbability, blockProbabilityNextCycle, blocksPerYear)
    }

    // ------------------------------
    // Utility functions (same as before)
    // ------------------------------

    fun calculateOfflineBlocks(
        previousOffline: Long?,
        online: Boolean,
        offlineStart: Long,
        currentBlock: Long,
    ): Long =
        if (online) {
            previousOffline ?: 0L
        } else if ((previousOffline ?: 0L) == 0L) {
            currentBlock - offlineStart
        } else {
            (previousOffline ?: 0L) + 1
        }

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

    fun blocksPerYear(blockProbability: BigDecimal): BigDecimal =
        BLOCKS_PER_YEAR.multiply(blockProbability)

    fun resolveStatus(exitBlock: BigInteger, rawStatus: BigInteger): Int =
        if (exitBlock == MAX_UINT32) rawStatus.toInt() else 4 // 4 = Exiting

    fun calculateNextCycleBlock(
        startBlock: BigInteger,
        completedPeriods: BigInteger,
        stakingPeriodLength: Int,
    ): Long =
        startBlock.toLong() + ((completedPeriods.toLong() + 1L) * stakingPeriodLength.toLong())

    fun calculateNextCycleStart(snapshot: ValidatorSnapshot, currentBlock: Long): Long {
        if (snapshot.startBlock == 0L) return 0L
        val offset = currentBlock - snapshot.startBlock
        val positionInCycle = offset % snapshot.stakingPeriodLength
        val currentCycleStart = currentBlock - positionInCycle
        return currentCycleStart + snapshot.stakingPeriodLength
    }

    fun calculateValidatorYield(
        validatorTvl: BigDecimal,
        delegatorTvl: BigDecimal,
        hasDelegations: Boolean,
        blocksPerYear: BigDecimal,
        vthoIssued: BigDecimal,
        vthoPrice: BigDecimal,
    ): Triple<BigDecimal, BigDecimal, BigDecimal> {
        val issuanceUsd = vthoIssued.multiply(vthoPrice)
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

    fun calculateNftLevelYields(
        nextPeriodWeight: BigDecimal,
        nextCycleEffectiveDelegationStake: BigDecimal,
        totalNextPeriodWeight: BigDecimal,
        vthoIssued: BigDecimal,
        vthoPriceUsd: BigDecimal,
        vetPriceUsd: BigDecimal,
        status: Status,
    ): Map<TokenLevel, Decimal128> {
        if (status == Status.EXITING) return emptyMap()
        return TokenLevel.entries
            .filter { it != TokenLevel.All }
            .mapNotNull { level ->
                val requiredUSD = level.staked * vetPriceUsd

                if (requiredUSD.compareTo(BigDecimal.ZERO) == 0) return@mapNotNull null

                val nftWeight = level.staked * BigDecimal(2)
                val adjustedTotal = totalNextPeriodWeight + nftWeight
                val adjustedValidator =
                    if (nextCycleEffectiveDelegationStake > BigDecimal.ZERO) {
                        nextPeriodWeight + nftWeight
                    } else {
                        nextPeriodWeight * BigDecimal(2) + nftWeight
                    }

                val blockProbabilityNextCycle =
                    adjustedValidator.divide(adjustedTotal, 6, RoundingMode.HALF_UP)
                val bpy = blocksPerYear(blockProbabilityNextCycle)

                val issuanceUsd = vthoIssued.multiply(vthoPriceUsd)
                val annualIssuanceUsd = bpy.multiply(issuanceUsd)

                val denom = nextCycleEffectiveDelegationStake + level.effectiveStake
                val nftDelegationShare =
                    if (denom.compareTo(BigDecimal.ZERO) == 0) {
                        BigDecimal.ZERO
                    } else {
                        level.effectiveStake.divide(denom, 12, RoundingMode.HALF_UP)
                    }

                val yieldPct =
                    annualIssuanceUsd // total rewards pool (USD/year)
                        .multiply(nftDelegationShare) // share for this NFT
                        .multiply(BigDecimal("0.7")) // delegator split
                        .divide(requiredUSD, 12, RoundingMode.HALF_UP) // normalize per USD staked
                        .multiply(BigDecimal(100)) // convert to %

                level to NumberUtils.toSafeDecimal128(yieldPct)
            }
            .toMap()
    }

    fun updatePendingValidatorVET(
        pendingVET: BigInteger?,
        existing: BigDecimal,
        lastBlock: Long,
        currentBlock: Long,
        startBlock: Long?,
        cyclePeriodLength: Long?,
    ): BigDecimal {
        if (pendingVET == null) return existing

        val pending = NumberUtils.toVET(pendingVET)

        // If config is missing or invalid, default to accumulation
        if (
            startBlock == null ||
                cyclePeriodLength == null ||
                startBlock == 0L ||
                cyclePeriodLength == 0L
        ) {
            return existing + pending
        }

        val isNewCycle =
            ((currentBlock - startBlock) / cyclePeriodLength) >
                ((lastBlock - startBlock) / cyclePeriodLength)

        return if (isNewCycle) pending else existing + pending
    }
}
