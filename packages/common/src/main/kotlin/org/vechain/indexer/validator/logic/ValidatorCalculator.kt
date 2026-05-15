package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.ValidatorSnapshot

/** Validator-related calculations shared by V2 projections and other indexers. */
object ValidatorCalculator {
    private val BLOCKS_PER_YEAR = BigDecimal("3155760") // 360 * 24 * 365.25
    private val MAX_VALIDATOR_STAKE = BigDecimal("600000000")

    /** Converts block probability to expected blocks per year. */
    fun blocksPerYear(blockProbability: BigDecimal): BigDecimal =
        BLOCKS_PER_YEAR.multiply(blockProbability)

    /**
     * Calculates the next cycle start for a validator snapshot.
     *
     * @return block number of next cycle start, or 0 if validator hasn't started.
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
     * @return Triple(validator yield %, tvl-based yield %, avg delegator yield %).
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
     * NFT levels whose staked VET would push the validator's `nextCycleStake` above 600M are
     * excluded.
     */
    fun calculateNftLevelYieldsIfDelegatedNextCycle(
        nextPeriodWeight: BigDecimal,
        nextPeriodVET: BigDecimal,
        nextCycleEffectiveDelegationStake: BigDecimal,
        totalNextPeriodWeight: BigDecimal,
        vthoPriceUsd: BigDecimal,
        vetPriceUsd: BigDecimal,
        status: Status,
        nextCycleStake: BigDecimal,
    ): Map<TokenLevel, BigDecimal> {
        if (status == Status.EXITING) {
            return emptyMap()
        }
        return TokenLevel.entries
            .filter { it != TokenLevel.All }
            .mapNotNull { level ->
                if (nextCycleStake + level.staked > MAX_VALIDATOR_STAKE) {
                    return@mapNotNull null
                }

                val requiredUSD = level.staked * vetPriceUsd
                if (requiredUSD.compareTo(BigDecimal.ZERO) == 0) {
                    return@mapNotNull null
                }

                val totalVET = nextPeriodVET + level.staked
                val vthoIssued = determineVTHOIssuedPerBlock(totalVET)

                val nftWeight = level.effectiveWeight
                val adjustedValidator =
                    if (nextCycleEffectiveDelegationStake > BigDecimal.ZERO) {
                        nextPeriodWeight + nftWeight
                    } else {
                        nextPeriodWeight * BigDecimal(2) + nftWeight
                    }

                val adjustedTotal = totalNextPeriodWeight - nextPeriodWeight + adjustedValidator

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
                    annualIssuanceUsd
                        .multiply(nftDelegationShare)
                        .multiply(BigDecimal("0.7"))
                        .divide(requiredUSD, 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))

                level to NumberUtils.toScaledDecimal(yieldPct)
            }
            .toMap()
    }

    /**
     * Calculates current-cycle NFT-level APYs for token levels already delegated to a validator.
     */
    fun calculateDelegatedNftLevelYieldsCurrentCycle(
        currentDelegatedLevels: Map<TokenLevel, Long>,
        blocksPerYear: BigDecimal,
        vthoIssued: BigDecimal,
        vthoPriceUsd: BigDecimal,
        vetPriceUsd: BigDecimal,
    ): Map<TokenLevel, BigDecimal> {
        if (currentDelegatedLevels.isEmpty()) {
            return emptyMap()
        }

        val totalEffectiveDelegations =
            currentDelegatedLevels.entries.fold(BigDecimal.ZERO) { acc, (level, count) ->
                acc.add(level.effectiveStake.multiply(BigDecimal.valueOf(count)))
            }

        if (totalEffectiveDelegations.compareTo(BigDecimal.ZERO) == 0) {
            return emptyMap()
        }

        val issuanceUsd = vthoIssued.multiply(vthoPriceUsd)
        val annualIssuanceUsd = blocksPerYear.multiply(issuanceUsd)

        return currentDelegatedLevels.keys.associateWith { level ->
            val requiredUSD = level.staked.multiply(vetPriceUsd)
            if (requiredUSD.compareTo(BigDecimal.ZERO) == 0) {
                BigDecimal.ZERO
            } else {
                val share =
                    level.effectiveStake.divide(totalEffectiveDelegations, 12, RoundingMode.HALF_UP)
                val yieldPct =
                    annualIssuanceUsd
                        .multiply(share)
                        .multiply(BigDecimal("0.7"))
                        .divide(requiredUSD, 12, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                NumberUtils.toScaledDecimal(yieldPct)
            }
        }
    }

    /**
     * VTHO issued per block using VeChain's formula: annual = 1200 * 64 * sqrt(VET_staked), divided
     * by blocks per year (365 days × 10s blocks).
     */
    fun determineVTHOIssuedPerBlock(totalVetStaked: BigDecimal?): BigDecimal {
        if (totalVetStaked == null || totalVetStaked <= BigDecimal.ZERO) {
            return BigDecimal.ZERO
        }

        val mc = MathContext(30, RoundingMode.HALF_UP)
        val annualVTHO = BigDecimal(76800).multiply(totalVetStaked.sqrt(mc), mc)
        val blocksPerYear = BigDecimal("3153600")
        return annualVTHO.divide(blocksPerYear, mc)
    }
}
