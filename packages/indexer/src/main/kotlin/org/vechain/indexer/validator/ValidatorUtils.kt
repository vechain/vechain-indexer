package org.vechain.indexer.validator

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import org.bson.types.Decimal128
import org.vechain.indexer.utils.NumberUtils

private val MAX_UINT32 = 4294967295
private val SCALE = BigDecimal("1000000000000") // 1e12

object ValidatorUtils {
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
    ): Pair<List<Validator>, List<String>> {
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

        val validators = mutableListOf<Validator>()

        // Convert oracle values -> BigDecimal (USD)
        val vetPrice = BigDecimal(vetPriceUsd).divide(SCALE, 12, RoundingMode.HALF_UP)
        val vthoPrice = BigDecimal(vthoPriceUsd).divide(SCALE, 12, RoundingMode.HALF_UP)

        for (i in masters.indices) {
            val existingDoc = existingDocs[masters[i]]

            val validatorVET = NumberUtils.toVET(validatorLockedVETs[i])
            val delegatorVET = NumberUtils.toVET(delegatorsStakes[i])
            val totalVET = validatorVET.add(delegatorVET)

            val validatorTvl = validatorVET.multiply(vetPrice)
            val delegatorTvl = delegatorVET.multiply(vetPrice)
            val totalTvl = validatorTvl.add(delegatorTvl)

            val vthoSupply = NumberUtils.toVET(totalVTHOSupply)

            var blocksOffline = existingDoc?.offlineBlocks ?: 0L
            if (!onlines[i]) {
                if (blocksOffline == 0L) {
                    blocksOffline = blockNumber - offlineBlocks[i].toLong()
                } else {
                    blocksOffline++
                }
            }

            val totalBlocks = blockNumber - (startBlocks[i].toLong())
            val percentageOffline =
                if (totalBlocks == 0L) {
                    BigDecimal.ZERO
                } else {
                    BigDecimal(blocksOffline)
                        .divide(BigDecimal(totalBlocks), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                }

            // Get previous total supply from existing doc, else use current
            val prevTotalVTHOSupply = existingDocs[masters[i]]?.totalVTHOSupply ?: vthoSupply

            val hasDelegations = delegatorsStakes[i] > BigInteger.ZERO

            val blockProbability =
                NumberUtils.toProbabilityOf(validatorLockedWeights[i], totalWeight)

            val blocksPerYear = BigDecimal.valueOf(360 * 24 * 365.25).multiply(blockProbability)

            // Calculate yields
            val (validatorYield, tvlBasedYield, avgDelegatorYield) =
                calculateValidatorYield(
                    validatorTvl = validatorTvl,
                    delegatorTvl = delegatorTvl,
                    blocksPerYear = blocksPerYear,
                    hasDelegations = hasDelegations,
                    totalVTHOSupply = vthoSupply,
                    prevTotalVTHOSupply = BigDecimal(prevTotalVTHOSupply.toString()),
                    vthoPrice = vthoPrice,
                )

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
                    offlineBlocks = blocksOffline,
                    stakingPeriodLength = stakingPeriodLengths[i].toLong(),
                    startBlock = startBlocks[i].toLong(),
                    completedPeriods = completedPeriods[i].toLong(),
                    vetStaked = toSafeDecimal128(totalVET),
                    validatorVetStaked = toSafeDecimal128(validatorVET),
                    delegatorVetStaked = toSafeDecimal128(delegatorVET),
                    totalWeight = toSafeDecimal128(NumberUtils.toVET(validatorLockedWeights[i])),
                    blockProbability = toSafeDecimal128(blockProbability),
                    blocksPerEpoch = toSafeDecimal128((blockProbability * BigDecimal(180))),
                    blocksPerYear = toSafeDecimal128(blocksPerYear),
                    validatorTvl = toSafeDecimal128(validatorTvl),
                    delegatorTvl = toSafeDecimal128(delegatorTvl),
                    totalTvl = toSafeDecimal128(totalTvl),
                    delegations = existingDocs[masters[i]]?.delegations ?: emptyMap(),
                    delegationIds = existingDocs[masters[i]]?.delegationIds ?: emptyMap(),
                    delegationIdList = existingDocs[masters[i]]?.delegationIdList ?: emptyList(),
                    validatorYield = toSafeDecimal128(validatorYield),
                    tvlBasedYield = toSafeDecimal128(tvlBasedYield),
                    avgDelegatorYield = toSafeDecimal128(avgDelegatorYield),
                    totalVTHOSupply = toSafeDecimal128(vthoSupply),
                    percentageOffline = toSafeDecimal128(percentageOffline),
                    version = (existingDoc?.version ?: 0) + 1,
                )

            validators.add(fresh)
        }

        val toDelete = existingDocs.keys.minus(masters.toSet()).toList()

        return validators to toDelete
    }

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

    private fun BigInteger.toLongOrNull(): Long? =
        if (this == BigInteger.valueOf(Long.MAX_VALUE)) null else this.toLong()

    private fun toSafeDecimal128(value: BigDecimal, scale: Int = 6): Decimal128 {
        // Limit precision to avoid Decimal128 overflow
        val scaled = value.setScale(scale, RoundingMode.HALF_UP)
        return Decimal128(scaled)
    }
}
