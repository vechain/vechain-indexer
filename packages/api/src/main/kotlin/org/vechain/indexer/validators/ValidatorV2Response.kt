package org.vechain.indexer.validators

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.math.RoundingMode
import org.vechain.indexer.prices.PriceFeedService
import org.vechain.indexer.stargate.token.TokenLevelDecimalValues
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.logic.ValidatorCalculator

/**
 * Public API representation of a [Validator] document. Matches the V1 wire surface so consumer
 * migration is a swap of the endpoint, not of the field set.
 *
 * Fields fall into three groups:
 * - **Stored** — copied straight from the V2 document.
 * - **Derived from V2 + aggregates** — `totalWeight`, `blockProbability`, `blocksPerYear`,
 *   `cycleEndBlock`, `percentageOffline`, etc. Computed in [from] using per-request aggregates.
 * - **Price-dependent** — TVL, yields, NFT yields. Null when `prices == null` (solo / custom
 *   networks without a deployed `PriceFeedOracle`); fully populated otherwise.
 *
 * Still deferred (separate workstreams):
 * - `online` — needs an "is recently proposed" recency threshold + current best block.
 * - `totalRewards` — depends on the validator-reward ledger (V1's `validator-reward` profile).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidatorV2Response(
    val id: String,
    val endorser: String?,
    val beneficiary: String?,
    val status: Status?,

    // ---- Stake breakdown ----
    val vetStaked: BigDecimal?,
    val validatorVetStaked: BigDecimal?,
    val delegatorVetStaked: BigDecimal?,
    val queuedVetStaked: BigDecimal?,
    val validatorQueuedVetStaked: BigDecimal?,
    val delegatorQueuedVetStaked: BigDecimal?,
    val exitingVetStaked: BigDecimal?,
    val validatorExitingVetStaked: BigDecimal?,
    val delegatorExitingVetStaked: BigDecimal?,

    // ---- Weight + probability ----
    val validatorLockedWeight: BigDecimal?,
    val totalNextPeriodWeight: BigDecimal?,
    val totalWeight: BigDecimal?,
    val blockProbability: BigDecimal?,
    val blocksPerYear: BigDecimal?,
    val blocksPerEpoch: Long = 180L,

    // ---- Cycle ----
    val startBlock: Long?,
    val exitBlock: Long?,
    val cyclePeriodLength: Long?,
    val cycleEndBlock: Long?,
    val completedPeriods: Long?,
    val queuePosition: Long?,
    val availableStartBlock: Long?,

    // ---- Liveness ----
    val scheduledBlocks: Long,
    val proposedBlocks: Long,
    val missedBlocks: Long,
    val percentageOffline: BigDecimal?,
    val lastProposedBlockNumber: Long?,
    val lastMissedBlockNumber: Long?,

    // ---- Price-dependent: TVL ----
    val validatorTvl: BigDecimal?,
    val delegatorTvl: BigDecimal?,
    val totalTvl: BigDecimal?,
    val validatorTvlPercentage: BigDecimal?,

    // ---- Price-dependent: yields ----
    val validatorYield: BigDecimal?,
    val tvlBasedYield: BigDecimal?,
    val avgDelegatorYield: BigDecimal?,
    val nextCycleValidatorYield: BigDecimal?,
    val nextCycleTvlBasedYield: BigDecimal?,
    val nextCycleAvgDelegatorYield: BigDecimal?,

    // ---- Price-dependent: NFT yields ----
    val nftYields: TokenLevelDecimalValues?,
    val nftYieldsIfDelegatedNextCycle: TokenLevelDecimalValues?,
) {
    companion object {
        fun from(
            v: Validator,
            aggregates: ValidatorAggregates,
            prices: PriceFeedService.Prices?,
        ): ValidatorV2Response {
            // --- stake derivations ---
            val validatorVet = v.validatorVetStaked ?: BigDecimal.ZERO
            val delegatorVet = v.delegatorVetStaked ?: BigDecimal.ZERO
            val totalLocked = validatorVet + delegatorVet

            val queued = v.queuedVetStaked ?: BigDecimal.ZERO
            val validatorQueued = v.validatorQueuedVetStaked ?: BigDecimal.ZERO
            val delegatorQueued = (queued - validatorQueued).max(BigDecimal.ZERO)

            val exiting = v.exitingVetStaked ?: BigDecimal.ZERO
            val validatorExiting = v.validatorExitingVetStaked ?: BigDecimal.ZERO
            val delegatorExiting = (exiting - validatorExiting).max(BigDecimal.ZERO)

            // --- cycle ---
            val startBlock = v.startBlock
            val completedPeriods = v.completedPeriods
            val cyclePeriodLength = v.cyclePeriodLength
            val cycleEndBlock =
                if (startBlock != null && completedPeriods != null && cyclePeriodLength != null) {
                    startBlock + (completedPeriods + 1L) * cyclePeriodLength
                } else null

            // --- weight + block probability (current cycle) ---
            val totalWeight = aggregates.totalWeight.takeIf { it > BigDecimal.ZERO }
            val validatorWeight = v.validatorLockedWeight
            val blockProbability =
                if (totalWeight != null && validatorWeight != null) {
                    validatorWeight.divide(totalWeight, 12, RoundingMode.HALF_UP)
                } else null
            val blocksPerYear = blockProbability?.let { ValidatorCalculator.blocksPerYear(it) }

            // --- percentage offline ---
            val percentageOffline =
                if (v.scheduledBlocks > 0L) {
                    BigDecimal(v.missedBlocks)
                        .divide(BigDecimal(v.scheduledBlocks), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                } else null

            // --- price-dependent block ---
            var validatorTvl: BigDecimal? = null
            var delegatorTvl: BigDecimal? = null
            var totalTvl: BigDecimal? = null
            var validatorTvlPercentage: BigDecimal? = null
            var validatorYield: BigDecimal? = null
            var tvlBasedYield: BigDecimal? = null
            var avgDelegatorYield: BigDecimal? = null
            var nextCycleValidatorYield: BigDecimal? = null
            var nextCycleTvlBasedYield: BigDecimal? = null
            var nextCycleAvgDelegatorYield: BigDecimal? = null
            var nftYields: TokenLevelDecimalValues? = null
            var nftYieldsIfDelegatedNextCycle: TokenLevelDecimalValues? = null

            if (prices != null) {
                val vetPrice = prices.vetUsd
                val vthoPrice = prices.vthoUsd

                // TVL (always derivable from prices alone)
                validatorTvl = validatorVet.multiply(vetPrice)
                delegatorTvl = delegatorVet.multiply(vetPrice)
                totalTvl = validatorTvl.add(delegatorTvl)
                validatorTvlPercentage =
                    if (totalTvl > BigDecimal.ZERO) {
                        validatorTvl.divide(totalTvl, 12, RoundingMode.HALF_UP)
                    } else null

                // Current-cycle yields (need blocksPerYear, totalWeight)
                if (blocksPerYear != null && totalLocked > BigDecimal.ZERO) {
                    val hasDelegations = delegatorVet > BigDecimal.ZERO
                    val vthoIssued = ValidatorCalculator.determineVTHOIssuedPerBlock(totalLocked)
                    val (vYield, tvlYield, avgYield) =
                        ValidatorCalculator.calculateValidatorYield(
                            validatorTvl = validatorTvl,
                            delegatorTvl = delegatorTvl,
                            hasDelegations = hasDelegations,
                            blocksPerYear = blocksPerYear,
                            vthoIssued = vthoIssued,
                            vthoPrice = vthoPrice,
                        )
                    validatorYield = vYield
                    tvlBasedYield = tvlYield
                    avgDelegatorYield = avgYield

                    nftYields =
                        TokenLevelDecimalValues.fromMap(
                            ValidatorCalculator.calculateDelegatedNftLevelYieldsCurrentCycle(
                                currentDelegatedLevels =
                                    aggregates.currentDelegatedLevelCounts(v.id),
                                blocksPerYear = blocksPerYear,
                                vthoIssued = vthoIssued,
                                vthoPriceUsd = vthoPrice,
                                vetPriceUsd = vetPrice,
                            )
                        )
                }

                // Next-cycle projections
                val nextCycleStake = (queued + totalLocked - exiting).max(BigDecimal.ZERO)
                val nextCycleValidatorStake =
                    (validatorVet + validatorQueued - validatorExiting).max(BigDecimal.ZERO)
                val nextCycleDelegatorStake =
                    (delegatorVet + delegatorQueued - delegatorExiting).max(BigDecimal.ZERO)
                val nextCycleValidatorTvl = nextCycleValidatorStake.multiply(vetPrice)
                val nextCycleDelegatorTvl = nextCycleDelegatorStake.multiply(vetPrice)

                val nextPeriodWeight = v.totalNextPeriodWeight
                val totalNextPeriodWeight = aggregates.totalNextPeriodWeight
                val blockProbabilityNextCycle =
                    if (nextPeriodWeight != null && totalNextPeriodWeight > BigDecimal.ZERO) {
                        nextPeriodWeight.divide(totalNextPeriodWeight, 12, RoundingMode.HALF_UP)
                    } else null
                val blocksPerYearNextCycle =
                    blockProbabilityNextCycle?.let { ValidatorCalculator.blocksPerYear(it) }

                if (blocksPerYearNextCycle != null && nextCycleStake > BigDecimal.ZERO) {
                    val nextHasDelegations = nextCycleDelegatorStake > BigDecimal.ZERO
                    val nextVthoIssued =
                        ValidatorCalculator.determineVTHOIssuedPerBlock(nextCycleStake)
                    val (ncV, ncT, ncA) =
                        ValidatorCalculator.calculateValidatorYield(
                            validatorTvl = nextCycleValidatorTvl,
                            delegatorTvl = nextCycleDelegatorTvl,
                            hasDelegations = nextHasDelegations,
                            blocksPerYear = blocksPerYearNextCycle,
                            vthoIssued = nextVthoIssued,
                            vthoPrice = vthoPrice,
                        )
                    nextCycleValidatorYield = ncV
                    nextCycleTvlBasedYield = ncT
                    nextCycleAvgDelegatorYield = ncA
                }

                // nftYieldsIfDelegatedNextCycle — uses MongoDB-derived next-cycle delegation stake
                // (Stargate.getDelegatorsEffectiveStake is not needed).
                if (
                    v.status != Status.EXITING &&
                        v.status != Status.EXITED &&
                        v.status != Status.WITHDRAWN &&
                        nextPeriodWeight != null &&
                        totalNextPeriodWeight > BigDecimal.ZERO &&
                        startBlock != null &&
                        completedPeriods != null &&
                        cyclePeriodLength != null
                ) {
                    val periodPlusTwoBlock =
                        startBlock + (completedPeriods + 2L) * cyclePeriodLength
                    val nextCycleEffectiveDelegationStake =
                        aggregates.nextCycleEffectiveDelegationStake(v.id, periodPlusTwoBlock)
                    val mapped =
                        ValidatorCalculator.calculateNftLevelYieldsIfDelegatedNextCycle(
                            nextPeriodWeight = nextPeriodWeight,
                            nextPeriodVET = nextCycleStake,
                            nextCycleEffectiveDelegationStake = nextCycleEffectiveDelegationStake,
                            totalNextPeriodWeight = totalNextPeriodWeight,
                            vthoPriceUsd = vthoPrice,
                            vetPriceUsd = vetPrice,
                            // ValidatorCalculator only checks for `Status.EXITING`; we pre-filter
                            // and pass a non-EXITING placeholder.
                            status = Status.ACTIVE,
                            nextCycleStake = nextCycleStake,
                        )
                    nftYieldsIfDelegatedNextCycle = TokenLevelDecimalValues.fromMap(mapped)
                }
            }

            return ValidatorV2Response(
                id = v.id,
                endorser = v.endorser,
                beneficiary = v.beneficiary,
                status = v.status,
                vetStaked = totalLocked.takeIf { it > BigDecimal.ZERO },
                validatorVetStaked = v.validatorVetStaked,
                delegatorVetStaked = v.delegatorVetStaked,
                queuedVetStaked = v.queuedVetStaked,
                validatorQueuedVetStaked = v.validatorQueuedVetStaked,
                delegatorQueuedVetStaked = delegatorQueued.takeIf { it > BigDecimal.ZERO },
                exitingVetStaked = v.exitingVetStaked,
                validatorExitingVetStaked = v.validatorExitingVetStaked,
                delegatorExitingVetStaked = delegatorExiting.takeIf { it > BigDecimal.ZERO },
                validatorLockedWeight = v.validatorLockedWeight,
                totalNextPeriodWeight = v.totalNextPeriodWeight,
                totalWeight = totalWeight,
                blockProbability = blockProbability,
                blocksPerYear = blocksPerYear,
                startBlock = startBlock,
                exitBlock = v.exitBlock,
                cyclePeriodLength = cyclePeriodLength,
                cycleEndBlock = cycleEndBlock,
                completedPeriods = completedPeriods,
                queuePosition = v.queuePosition,
                availableStartBlock = v.availableStartBlock,
                scheduledBlocks = v.scheduledBlocks,
                proposedBlocks = v.proposedBlocks,
                missedBlocks = v.missedBlocks,
                percentageOffline = percentageOffline,
                lastProposedBlockNumber = v.lastProposedBlockNumber,
                lastMissedBlockNumber = v.lastMissedBlockNumber,
                validatorTvl = validatorTvl,
                delegatorTvl = delegatorTvl,
                totalTvl = totalTvl,
                validatorTvlPercentage = validatorTvlPercentage,
                validatorYield = validatorYield,
                tvlBasedYield = tvlBasedYield,
                avgDelegatorYield = avgDelegatorYield,
                nextCycleValidatorYield = nextCycleValidatorYield,
                nextCycleTvlBasedYield = nextCycleTvlBasedYield,
                nextCycleAvgDelegatorYield = nextCycleAvgDelegatorYield,
                nftYields = nftYields,
                nftYieldsIfDelegatedNextCycle = nftYieldsIfDelegatedNextCycle,
            )
        }
    }
}
