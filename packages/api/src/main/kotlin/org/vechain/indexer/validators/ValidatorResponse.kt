@file:Suppress("DEPRECATION") // Backs deprecated V1 validator endpoints; uses V1 Status enum.

package org.vechain.indexer.validators

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import org.vechain.indexer.stargate.token.TokenLevelDecimalValues
import org.vechain.indexer.utils.NumberUtils
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator

/**
 * Wire shape returned by the deprecated V1 validator endpoints. Now sourced from [Validator] via
 * [ValidatorV2Response.from]; this type only re-shapes the V2 response into the V1 field names that
 * existing clients consume. Will be deleted alongside the V1 endpoints.
 *
 * Choices to preserve V1's existing wire shape:
 * - `online` is hardcoded `true` (V2 doesn't compute a recency-based liveness flag; V1 is on its
 *   way out so we keep the field truthful-by-default rather than null).
 * - `totalRewards` is `null` (V1 declared it but never populated it).
 * - `offlineBlocks` is sourced from V2's `missedSlots` (PoS-schedule misses), numerically different
 *   from V1's transient `OfflineBlock` pointer. Field name preserved for compatibility.
 * - Stake fields coalesce to zero (V1 always emitted `0` rather than dropping the key under
 *   `@JsonInclude(NON_NULL)`).
 * - All `BigDecimal` outputs are scaled to 6 dp via [NumberUtils.toScaledDecimal] to match V1's
 *   historical format.
 *
 * V1-only re-derivations from V2 fields (V2 redefined the semantics):
 * - `blocksPerEpoch` — V1 published `blockProbability × 180` (this validator's expected blocks per
 *   epoch); V2 publishes the epoch length constant (180). Reconstructed here.
 * - `totalWeight` — V1 published this validator's own `validatorLockedWeight`; V2 publishes the
 *   chain-wide sum. The per-validator value is reconstructed from `v2.validatorLockedWeight`.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidatorResponse(
    val id: String,
    val endorser: String?,
    val beneficiary: String?,
    val status: Status?,
    val vetStaked: BigDecimal,
    val validatorVetStaked: BigDecimal,
    val delegatorVetStaked: BigDecimal,
    val queuedVetStaked: BigDecimal,
    val validatorQueuedVetStaked: BigDecimal,
    val delegatorQueuedVetStaked: BigDecimal,
    val validatorExitingVetStaked: BigDecimal,
    val delegatorExitingVetStaked: BigDecimal,
    val exitingVetStaked: BigDecimal,
    val cycleEndBlock: Long?,
    val totalRewards: BigDecimal?,
    val blockProbability: BigDecimal?,
    val blocksPerEpoch: BigDecimal?,
    val totalTvl: BigDecimal,
    val validatorTvl: BigDecimal,
    val delegatorTvl: BigDecimal,
    val tvlBasedYield: BigDecimal?,
    val validatorYield: BigDecimal?,
    val avgDelegatorYield: BigDecimal?,
    val nextCycleTvlBasedYield: BigDecimal?,
    val nextCycleValidatorYield: BigDecimal?,
    val nextCycleAvgDelegatorYield: BigDecimal?,
    val nftYieldsIfDelegatedNextCycle: TokenLevelDecimalValues?,
    val nftYields: TokenLevelDecimalValues?,
    val totalWeight: BigDecimal?,
    val online: Boolean,
    val completedPeriods: Long?,
    val startBlock: Long?,
    val cyclePeriodLength: Long?,
    val blocksPerYear: BigDecimal?,
    val percentageOffline: BigDecimal?,
    val offlineBlocks: Long?,
    val exitBlock: Long?,
    val queuePosition: Long?,
    val availableStartBlock: Long?,
) {
    companion object {
        fun from(
            v: Validator,
            aggregates: ValidatorAggregates,
            vetPrice: BigDecimal,
            vthoPrice: BigDecimal,
        ): ValidatorResponse {
            val v2 = ValidatorV2Response.from(v, aggregates, vetPrice, vthoPrice)
            return ValidatorResponse(
                id = v2.id,
                endorser = v2.endorser,
                beneficiary = v2.beneficiary,
                status = v2.status,
                vetStaked = v2.vetStaked.scaledOrZero(),
                validatorVetStaked = v2.validatorVetStaked.scaledOrZero(),
                delegatorVetStaked = v2.delegatorVetStaked.scaledOrZero(),
                queuedVetStaked = v2.queuedVetStaked.scaledOrZero(),
                validatorQueuedVetStaked = v2.validatorQueuedVetStaked.scaledOrZero(),
                delegatorQueuedVetStaked = v2.delegatorQueuedVetStaked.scaledOrZero(),
                validatorExitingVetStaked = v2.validatorExitingVetStaked.scaledOrZero(),
                delegatorExitingVetStaked = v2.delegatorExitingVetStaked.scaledOrZero(),
                exitingVetStaked = v2.exitingVetStaked.scaledOrZero(),
                cycleEndBlock = v2.cycleEndBlock,
                totalRewards = null,
                blockProbability = v2.blockProbability.scaled(),
                blocksPerEpoch =
                    v2.blockProbability?.multiply(BigDecimal.valueOf(v2.blocksPerEpoch)).scaled(),
                totalTvl = NumberUtils.toScaledDecimal(v2.totalTvl),
                validatorTvl = NumberUtils.toScaledDecimal(v2.validatorTvl),
                delegatorTvl = NumberUtils.toScaledDecimal(v2.delegatorTvl),
                tvlBasedYield = v2.tvlBasedYield.scaled(),
                validatorYield = v2.validatorYield.scaled(),
                avgDelegatorYield = v2.avgDelegatorYield.scaled(),
                nextCycleTvlBasedYield = v2.nextCycleTvlBasedYield.scaled(),
                nextCycleValidatorYield = v2.nextCycleValidatorYield.scaled(),
                nextCycleAvgDelegatorYield = v2.nextCycleAvgDelegatorYield.scaled(),
                nftYieldsIfDelegatedNextCycle = v2.nftYieldsIfDelegatedNextCycle,
                nftYields = v2.nftYields,
                totalWeight = v2.validatorLockedWeight.scaled(),
                online = true,
                completedPeriods = v2.completedPeriods,
                startBlock = v2.startBlock,
                cyclePeriodLength = v2.cyclePeriodLength,
                blocksPerYear = v2.blocksPerYear.scaled(),
                percentageOffline = v2.missedSlotsPercentage.scaled(),
                offlineBlocks = v2.missedSlots,
                exitBlock = v2.exitBlock,
                queuePosition = v2.queuePosition,
                availableStartBlock = v2.availableStartBlock,
            )
        }

        private fun BigDecimal?.scaled(): BigDecimal? =
            this?.let { NumberUtils.toScaledDecimal(it) }

        private fun BigDecimal?.scaledOrZero(): BigDecimal =
            NumberUtils.toScaledDecimal(this ?: BigDecimal.ZERO)
    }
}
