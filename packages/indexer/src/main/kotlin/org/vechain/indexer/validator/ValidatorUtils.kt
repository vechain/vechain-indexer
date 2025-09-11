package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.vechain.indexer.utils.NumberUtils

val MAX_UINT32 = 4294967295

fun unpackValidators(
    decoded: Map<String, Any?>,
    existingDocs: Map<String, Validator>,
    stakerBalance: BigInteger,
    totalStake: BigInteger,
    totalWeight: BigInteger,
    queuedStake: BigInteger,
    totalVTHOSupply: BigInteger,
    totalVTHOBurned: BigInteger,
    blockId: String,
    blockNumber: Long,
    blockTimestamp: Long,
): List<Validator> {
    val masters = decoded["masters"] as List<String>
    val endorsors = decoded["endorsors"] as List<String>
    val statuses = decoded["statuses"] as List<BigInteger>
    val onlines = decoded["onlines"] as List<Boolean>
    val offlineBlocks = decoded["offlineBlocks"] as List<BigInteger>
    val stakingPeriodLengths = decoded["stakingPeriodLengths"] as List<Int>
    val startBlocks = decoded["startBlocks"] as List<BigInteger>
    val exitBlocks = decoded["exitBlocks"] as List<BigInteger>
    val completedPeriods = decoded["completedPeriods"] as List<BigInteger>
    val validatorLockedVETs = decoded["validatorLockedStakes"] as List<BigInteger>
    val validatorLockedWeights = decoded["validatorLockedWeights"] as List<BigInteger>
    val delegatorsStakes = decoded["delegatorsStake"] as List<BigInteger>
    val validatorQueuedStakes = decoded["validatorQueuedStakes"] as List<BigInteger>
    val totalQueuedStakes = decoded["totalQueuedStakes"] as List<BigInteger>
    val exitingStakes = decoded["totalExitingStakes"] as List<BigInteger>
    val nextPeriodWeights = decoded["totalNextPeriodWeights"] as List<BigInteger>

    val validators = mutableListOf<Validator>()

    for (i in masters.indices) {
        val validatorVET = NumberUtils.toVET(validatorLockedVETs[i])
        val delegatorVET = NumberUtils.toVET(delegatorsStakes[i])
        val vetPrice = BigDecimal("0.02337") // USD price per VET

        val validatorTvl = validatorVET.multiply(vetPrice)
        val delegatorTvl = delegatorVET.multiply(vetPrice)
        val totalTvl = validatorTvl.add(delegatorTvl)

        val vthoSupply = NumberUtils.toVET(totalVTHOSupply)

        // Get previous total supply from existing doc, else use current
        val prevTotalVTHOSupply = existingDocs[masters[i]]?.totalVTHOSupply ?: vthoSupply

        val hasDelegations = delegatorsStakes[i] > BigInteger.ZERO

        // Calculate yields
        val (validatorYield, tvlBasedYield, avgDelegatorYield) =
            calculateValidatorYield(
                validatorTvl = validatorTvl,
                delegatorTvl = delegatorTvl,
                hasDelegations = hasDelegations,
                totalVTHOSupply = totalVTHOSupply.toBigDecimal(),
                prevTotalVTHOSupply = prevTotalVTHOSupply,
            )

        val blockProbabilty = NumberUtils.toProbabilityOf(validatorLockedWeights[i], totalWeight)

        val status =
            exitBlocks[i].let {
                if (it.toLongOrNull() == MAX_UINT32) statuses[i].toInt() else 4
            } // 4 = Exiting

        val fresh =
            Validator(
                id = masters[i],
                blockId = blockId,
                blockNumber = blockNumber,
                blockTimestamp = blockTimestamp,
                endorser = endorsors[i],
                status = Status.fromCode(status),
                online = onlines[i],
                // offlineBlocks = offlineBlocks[i].toLongOrNull(),
                stakingPeriodLength = stakingPeriodLengths[i].toLong(),
                startBlock = startBlocks[i].toLong(),
                cycleEndblock = exitBlocks[i].toLongOrNull(),
                completedPeriods = completedPeriods[i].toLong(),
                vetStaked = validatorVET.toString(),
                totalWeight = NumberUtils.toVET(validatorLockedWeights[i]).toString(),
                blockProbability = blockProbabilty.toString(),
                blocksPerEpoch = (blockProbabilty * BigDecimal(180)).toString(),
                validatorTvl = validatorTvl.toPlainString(),
                delegatorTvl = delegatorTvl.toPlainString(),
                totalTvl = totalTvl.toPlainString(),
                delegations = emptyMap(),
                delegationIds = emptyMap(),
                validatorYield = validatorYield.toString(),
                tvlBasedYield = tvlBasedYield.toString(),
                avgDelegatorYield = avgDelegatorYield.toString(),
                totalVTHOSupply = vthoSupply,
            )

        val old = existingDocs[fresh.id]
        validators.add(fresh)
    }

    return validators
}

private fun calculateValidatorYield(
    validatorTvl: BigDecimal,
    delegatorTvl: BigDecimal,
    hasDelegations: Boolean,
    totalVTHOSupply: BigDecimal,
    prevTotalVTHOSupply: BigDecimal,
): Triple<Double, Double, Double> {
    val vthoPrice = BigDecimal("0.001883") // USD/VTHO
    val blocksPerYear = BigDecimal.valueOf(360 * 24 * 365.25)

    // Issuance in USD
    val issuance = totalVTHOSupply.subtract(prevTotalVTHOSupply).max(BigDecimal.ZERO)
    val issuanceUsd = issuance.multiply(vthoPrice)
    val annualIssuanceUsd = blocksPerYear.multiply(issuanceUsd)

    val totalTvl = validatorTvl.add(delegatorTvl)
    if (totalTvl.compareTo(BigDecimal.ZERO) == 0) {
        return Triple(0.0, 0.0, 0.0)
    }

    val validatorPct = validatorTvl.divide(totalTvl, 6, RoundingMode.HALF_UP)

    val validatorYield =
        if (validatorTvl > BigDecimal.ZERO) {
            if (hasDelegations) {
                annualIssuanceUsd
                    .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("0.3"))
                    .multiply(BigDecimal(100))
                    .toDouble()
            } else {
                annualIssuanceUsd
                    .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toDouble()
            }
        } else {
            0.0
        }

    val tvlBasedYield =
        if (validatorTvl > BigDecimal.ZERO) {
            if (hasDelegations) {
                annualIssuanceUsd
                    .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                    .multiply(validatorPct)
                    .multiply(BigDecimal(100))
                    .toDouble()
            } else {
                annualIssuanceUsd
                    .divide(validatorTvl, 12, RoundingMode.HALF_UP)
                    .multiply(BigDecimal(100))
                    .toDouble()
            }
        } else {
            0.0
        }

    val avgDelegatorYield =
        if (hasDelegations && delegatorTvl > BigDecimal.ZERO) {
            annualIssuanceUsd
                .divide(delegatorTvl, 12, RoundingMode.HALF_UP)
                .multiply(BigDecimal("0.7"))
                .multiply(BigDecimal(100))
                .toDouble()
        } else {
            0.0
        }

    return Triple(validatorYield, tvlBasedYield, avgDelegatorYield)
}

/*fun mergeValidator(
    fresh: Validator,
    old: Validator?,
    totalStake: List<BigInteger>,
    totalSupply: BigInteger,
    totalBurned: BigInteger,
): Validator {
    val totalVET = totalStake[0].toBigDecimal()
    val validatorTvl = fresh.validatorTvl?.toBigDecimal() ?: BigDecimal.ZERO
    val totalTvl = fresh.totalTvl?.toBigDecimal() ?: BigDecimal.ZERO

    // Validator % of global stake
    val validatorPct =
        if (totalVET > BigDecimal.ZERO) {
            validatorTvl.divide(totalVET, 6, RoundingMode.HALF_UP).toDouble()
        } else {
            null
        }

    // Example: yield = burned / supply as a proxy
    val baseYield =
        if (totalSupply > BigInteger.ZERO) {
            totalBurned.toBigDecimal().divide(totalSupply.toBigDecimal(), 6, RoundingMode.HALF_UP).toDouble()
        } else {
            0.0
        }

    val validatorYield = baseYield * (validatorPct ?: 1.0)
    val avgDelegatorYield = baseYield * (1 - (validatorPct ?: 0.0))

    return fresh.copy(
        delegations = old?.delegations ?: emptyMap(),
        delegationIds = old?.delegationIds ?: emptyMap(),
        totalRewards = old?.totalRewards,
        validatorTvlPercentage = validatorPct,
        tvlBasedYield = baseYield,
        validatorYield = validatorYield,
        avgDelegatorYield = avgDelegatorYield,
    )
}*/

private fun BigInteger.toLongOrNull(): Long? =
    if (this == BigInteger.valueOf(Long.MAX_VALUE)) null else this.toLong()

private operator fun BigInteger.plus(other: BigInteger): BigInteger = this.add(other)

private operator fun BigInteger.minus(other: BigInteger): BigInteger = this.subtract(other)
