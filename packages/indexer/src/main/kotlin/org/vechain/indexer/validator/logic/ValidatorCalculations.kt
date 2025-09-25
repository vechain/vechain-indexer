package org.vechain.indexer.validator.logic

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

object ValidatorCalculations {
    private val SCALE = BigDecimal("1000000000000") // 1e12
    private val MAX_UINT32 = BigInteger.valueOf(4294967295L)

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
}
