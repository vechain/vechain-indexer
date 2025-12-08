package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import java.math.MathContext
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

    // ------------------------------
    // Core calculations
    // ------------------------------

    /**
     * Computes stake balances for a validator.
     *
     * @param row decoded validator row from chain state
     * @param existingDoc current persisted validator document (used to carry over exit stakes)
     * @param blockNumber current block number
     * @return a [Stakes] object with validator/delegator VET, queued/exiting stakes, and next-cycle
     *   stakes
     */
    fun computeStakes(
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

    /**
     * Computes TVL (Total Value Locked) in USD terms.
     *
     * @param stakes validator and delegator stakes in VET
     * @param vetPrice VET → USD conversion rate
     * @return a [Tvl] object containing validator, delegator, and total TVL in USD
     */
    fun computeTVL(stakes: Stakes, vetPrice: BigDecimal): Tvl =
        Tvl(
            validatorTvl = stakes.validatorVET * vetPrice,
            delegatorTvl = stakes.delegatorVET * vetPrice,
            totalTvl = (stakes.validatorVET * vetPrice) + (stakes.delegatorVET * vetPrice),
        )

    /**
     * Computes offline statistics for a validator.
     *
     * @param existingDoc current persisted validator document
     * @param row decoded validator row from chain state
     * @param blockNumber current block number
     * @return [OfflineStats] containing total offline blocks and percentage offline
     */
    fun computeOffline(
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

    /**
     * Computes block production probabilities for the current and next cycle.
     *
     * @param row decoded validator row
     * @param totalWeight total weight of all validators in the current cycle
     * @param nextPeriodTotalWeight total weight of all validators in the next cycle
     * @return [Probabilities] containing current probability, next-cycle probability, and blocks
     *   per year
     */
    fun computeProbabilities(
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
    // Utility functions
    // ------------------------------

    /**
     * Calculates total number of offline blocks for a validator.
     *
     * @param previousOffline previously recorded offline blocks
     * @param online whether the validator is currently online
     * @param offlineStart the block number when the validator first went offline
     * @param currentBlock current block number
     * @return updated offline block count
     */
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

    /**
     * Calculates percentage of time spent offline since start block.
     *
     * @param blocksOffline number of offline blocks
     * @param startBlock validator's start block
     * @param currentBlock current block number
     * @return percentage offline as a BigDecimal (0–100)
     */
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

    /**
     * Converts block probability to expected blocks per year.
     *
     * @param blockProbability probability of producing a block
     * @return number of blocks expected per year
     */
    fun blocksPerYear(blockProbability: BigDecimal): BigDecimal =
        BLOCKS_PER_YEAR.multiply(blockProbability)

    /**
     * Resolves a validator's status.
     *
     * If exitBlock != MAX_UINT32, then validator is exiting (status = 4).
     *
     * @param exitBlock exit block number
     * @param rawStatus raw status code from chain
     * @return resolved status code
     */
    fun resolveStatus(exitBlock: BigInteger, rawStatus: BigInteger): Int =
        if (exitBlock == MAX_UINT32) rawStatus.toInt() else 4 // 4 = Exiting

    /**
     * Calculates the block number at which the next cycle will start.
     *
     * @param startBlock validator start block
     * @param completedPeriods number of completed periods
     * @param stakingPeriodLength length of each staking period
     * @return next cycle start block
     */
    fun calculateNextCycleBlock(
        startBlock: BigInteger,
        completedPeriods: BigInteger,
        stakingPeriodLength: Int,
    ): Long =
        startBlock.toLong() + ((completedPeriods.toLong() + 1L) * stakingPeriodLength.toLong())

    /**
     * Calculates the next cycle start for a validator snapshot.
     *
     * @param snapshot validator snapshot
     * @param currentBlock current block number
     * @return block number of next cycle start, or 0 if validator hasn't started
     */
    fun calculateNextCycleStart(snapshot: ValidatorSnapshot, currentBlock: Long): Long {
        if (snapshot.startBlock == 0L) return 0L
        val offset = currentBlock - snapshot.startBlock
        val positionInCycle = offset % snapshot.stakingPeriodLength
        val currentCycleStart = currentBlock - positionInCycle
        return currentCycleStart + snapshot.stakingPeriodLength
    }

    /**
     * Calculates annualized yields for a validator.
     *
     * @param validatorTvl validator TVL in USD
     * @param delegatorTvl delegator TVL in USD
     * @param hasDelegations whether the validator has active delegations
     * @param blocksPerYear expected number of blocks produced per year
     * @param vthoIssued amount of VTHO issued per block
     * @param vthoPrice VTHO price in USD
     * @return Triple(validator yield %, tvl-based yield %, avg delegator yield %)
     */
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

    /**
     * Calculates expected NFT-level APYs if they delegate to this validator from the next cycle.
     *
     * @param nextPeriodWeight validator's next-period weight
     * @param nextCycleEffectiveDelegationStake delegator stake for the next cycle
     * @param totalNextPeriodWeight total validator weight in next period
     * @param vthoIssued amount of VTHO issued per block
     * @param vthoPriceUsd VTHO price in USD
     * @param vetPriceUsd VET price in USD
     * @param status current validator status
     * @param validatorAddress optional validator address for debug logging
     * @return map of TokenLevel → yield (Decimal128)
     */
    fun calculateNftLevelYields(
        nextPeriodWeight: BigDecimal,
        nextPeriodVET: BigDecimal,
        nextCycleEffectiveDelegationStake: BigDecimal,
        totalNextPeriodWeight: BigDecimal,
        vthoPriceUsd: BigDecimal,
        vetPriceUsd: BigDecimal,
        status: Status,
        validatorAddress: String? = null,
    ): Map<TokenLevel, Decimal128> {
        val debugValidator = "0x3970329d7964b9eaa7244c936d18b0846522cd96"
        val shouldLog = validatorAddress?.lowercase() == debugValidator

        if (shouldLog) {
            println("=== calculateNftLevelYields DEBUG for $validatorAddress ===")
            println("INPUT: nextPeriodWeight=$nextPeriodWeight")
            println("INPUT: nextCycleEffectiveDelegationStake=$nextCycleEffectiveDelegationStake")
            println("INPUT: totalNextPeriodWeight=$totalNextPeriodWeight")
            println("INPUT: vthoPriceUsd=$vthoPriceUsd")
            println("INPUT: vetPriceUsd=$vetPriceUsd")
            println("INPUT: status=$status")
        }

        if (status == Status.EXITING) {
            return emptyMap()
        }
        return TokenLevel.entries
            .filter { it != TokenLevel.All }
            .mapNotNull { level ->
                val requiredUSD = level.staked * vetPriceUsd

                val totalVET = nextPeriodVET + level.staked
                val vthoIssued = determineVTHOIssuedPerBlock(totalVET)
                println("INPUT: vthoIssued=$vthoIssued")

                if (shouldLog) {
                    println("--- Level: ${level.name} ---")
                    println("  level.staked=${level.staked}")
                    println("  requiredUSD=$requiredUSD")
                }

                if (requiredUSD.compareTo(BigDecimal.ZERO) == 0) {
                    if (shouldLog) println("  SKIP: requiredUSD is zero")
                    return@mapNotNull null
                }

                val nftWeight = level.effectiveWeight
                if (shouldLog) println("  nftWeight (level.effectiveWeight)=$nftWeight")

                val adjustedValidator =
                    if (nextCycleEffectiveDelegationStake > BigDecimal.ZERO) {
                        if (shouldLog)
                            println(
                                "  BRANCH: nextCycleEffectiveDelegationStake > 0, using nextPeriodWeight + nftWeight"
                            )
                        nextPeriodWeight + nftWeight
                    } else {
                        if (shouldLog)
                            println(
                                "  BRANCH: nextCycleEffectiveDelegationStake <= 0, using nextPeriodWeight * 2 + nftWeight"
                            )
                        nextPeriodWeight * BigDecimal(2) + nftWeight
                    }
                if (shouldLog) println("  adjustedValidator=$adjustedValidator")

                val adjustedTotal = totalNextPeriodWeight - nextPeriodWeight + adjustedValidator
                if (shouldLog)
                    println(
                        "  adjustedTotal=$adjustedTotal (totalNextPeriodWeight - nextPeriodWeight + adjustedValidator)"
                    )

                val blockProbabilityNextCycle =
                    adjustedValidator.divide(adjustedTotal, 6, RoundingMode.HALF_UP)
                if (shouldLog) println("  blockProbabilityNextCycle=$blockProbabilityNextCycle")

                val bpy = blocksPerYear(blockProbabilityNextCycle)
                if (shouldLog) println("  blocksPerYear=$bpy")

                val issuanceUsd = vthoIssued.multiply(vthoPriceUsd)
                if (shouldLog) println("  issuanceUsd=$issuanceUsd (vthoIssued * vthoPriceUsd)")

                val annualIssuanceUsd = bpy.multiply(issuanceUsd)
                if (shouldLog) println("  annualIssuanceUsd=$annualIssuanceUsd (bpy * issuanceUsd)")

                val denom = nextCycleEffectiveDelegationStake + level.effectiveStake
                if (shouldLog) println("  level.effectiveStake=${level.effectiveStake}")
                if (shouldLog)
                    println(
                        "  denom=$denom (nextCycleEffectiveDelegationStake + level.effectiveStake)"
                    )

                val nftDelegationShare =
                    if (denom.compareTo(BigDecimal.ZERO) == 0) {
                        if (shouldLog) println("  BRANCH: denom is zero, nftDelegationShare=0")
                        BigDecimal.ZERO
                    } else {
                        val share = level.effectiveStake.divide(denom, 12, RoundingMode.HALF_UP)
                        if (shouldLog) println("  nftDelegationShare=$share")
                        share
                    }

                val yieldPct =
                    annualIssuanceUsd // total rewards pool (USD/year)
                        .multiply(nftDelegationShare) // share for this NFT
                        .multiply(BigDecimal("0.7")) // delegator split
                        .divide(requiredUSD, 12, RoundingMode.HALF_UP) // normalize per USD staked
                        .multiply(BigDecimal(100)) // convert to %

                if (shouldLog) {
                    println("  YIELD CALCULATION:")
                    println(
                        "    annualIssuanceUsd * nftDelegationShare = ${annualIssuanceUsd.multiply(nftDelegationShare)}"
                    )
                    println(
                        "    * 0.7 (delegator split) = ${annualIssuanceUsd.multiply(nftDelegationShare).multiply(BigDecimal("0.7"))}"
                    )
                    println(
                        "    / requiredUSD ($requiredUSD) = ${annualIssuanceUsd.multiply(nftDelegationShare).multiply(BigDecimal("0.7")).divide(requiredUSD, 12, RoundingMode.HALF_UP)}"
                    )
                    println("    * 100 = $yieldPct")
                    println("  FINAL yieldPct=$yieldPct")
                }

                level to NumberUtils.toSafeDecimal128(yieldPct)
            }
            .toMap()
    }

    /**
     * Updates the pending VET amount for a validator.
     *
     * Accumulates pending stake until a new cycle starts, then resets.
     *
     * @param pendingVET pending stake amount in raw units
     * @param existing existing pending stake (carried forward)
     * @param lastBlock last processed block
     * @param currentBlock current block
     * @param startBlock validator start block (nullable)
     * @param cyclePeriodLength cycle length (nullable)
     * @return updated pending VET
     */
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

    /**
     * @param totalVetStaked Total VET staked in Wei (BigInteger).
     * @return VTHO issued per block in Wei (BigInteger).
     * @notice Calculate VTHO issued per block using VeChain formula: annual = 1200 * 64 *
     *   sqrt(VET_staked)
     */
    fun determineVTHOIssuedPerBlock(totalVetStaked: BigDecimal?): BigDecimal {
        if (totalVetStaked == null || totalVetStaked <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }

        val mc = MathContext(30, RoundingMode.HALF_UP)

        // Apply formula: 1200 * 64 * sqrt(VET_staked)
        val annualVTHO = BigDecimal(76800).multiply(totalVetStaked.sqrt(mc), mc)

        // Blocks per year (365 days, 10s per block)
        val blocksPerYear = BigDecimal("3153600")

        // VTHO per block (as decimal in VTHO)
        val vthoPerBlock = annualVTHO.divide(blocksPerYear, mc)

        // Convert back to Wei (×10^18) and return BigInteger
        return vthoPerBlock.multiply(BigDecimal.TEN.pow(18))
    }

    // ------------------------------
    // Data models
    // ------------------------------

    /** Represents validator and delegator stake breakdowns. */
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

    /** Represents total value locked (TVL) distribution between validator and delegators. */
    data class Tvl(
        val validatorTvl: BigDecimal,
        val delegatorTvl: BigDecimal,
        val totalTvl: BigDecimal,
    )

    /** Represents offline performance statistics. */
    data class OfflineStats(val blocksOffline: Long, val percentageOffline: BigDecimal)

    /** Represents validator block production probabilities. */
    data class Probabilities(
        val blockProbability: BigDecimal,
        val blockProbabilityNextCycle: BigDecimal,
        val blocksPerYear: BigDecimal,
    )
}
