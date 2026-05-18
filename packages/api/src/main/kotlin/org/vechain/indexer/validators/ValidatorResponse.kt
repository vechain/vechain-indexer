@file:Suppress("DEPRECATION") // Backs deprecated V1 validator endpoints; uses V1 Status enum.

package org.vechain.indexer.validators

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import org.vechain.indexer.prices.PriceFeedService
import org.vechain.indexer.stargate.token.TokenLevelDecimalValues
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator

/**
 * Wire shape returned by the deprecated V1 validator endpoints. Now sourced from [Validator] via
 * [ValidatorV2Response.from]; this type only re-shapes the V2 response into the V1 field names that
 * existing clients consume. Will be deleted alongside the V1 endpoints.
 *
 * Fields that V2 does not yet populate are returned as `null`:
 * - `online` — V2 doesn't compute a recency-based liveness flag.
 * - `totalRewards` — V1 declared but never populated this; preserved as null.
 *
 * `offlineBlocks` is sourced from V2's `missedBlocks` (PoS-schedule misses). The number is not
 * identical to V1's transient `OfflineBlock` pointer; the field name is preserved for client
 * compatibility.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidatorResponse(
    val id: String,
    val endorser: String?,
    val beneficiary: String?,
    val status: Status?,
    val vetStaked: BigDecimal?,
    val validatorVetStaked: BigDecimal?,
    val delegatorVetStaked: BigDecimal?,
    val queuedVetStaked: BigDecimal?,
    val validatorQueuedVetStaked: BigDecimal?,
    val delegatorQueuedVetStaked: BigDecimal?,
    val validatorExitingVetStaked: BigDecimal?,
    val delegatorExitingVetStaked: BigDecimal?,
    val exitingVetStaked: BigDecimal?,
    val cycleEndBlock: Long?,
    val totalRewards: BigDecimal?,
    val blockProbability: BigDecimal?,
    val blocksPerEpoch: BigDecimal?,
    val totalTvl: BigDecimal?,
    val validatorTvl: BigDecimal?,
    val delegatorTvl: BigDecimal?,
    val validatorTvlPercentage: BigDecimal?,
    val tvlBasedYield: BigDecimal?,
    val validatorYield: BigDecimal?,
    val avgDelegatorYield: BigDecimal?,
    val nextCycleTvlBasedYield: BigDecimal?,
    val nextCycleValidatorYield: BigDecimal?,
    val nextCycleAvgDelegatorYield: BigDecimal?,
    val nftYieldsIfDelegatedNextCycle: TokenLevelDecimalValues?,
    val nftYields: TokenLevelDecimalValues?,
    val totalWeight: BigDecimal?,
    val online: Boolean?,
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
            prices: PriceFeedService.Prices?,
        ): ValidatorResponse {
            val v2 = ValidatorV2Response.from(v, aggregates, prices)
            return ValidatorResponse(
                id = v2.id,
                endorser = v2.endorser,
                beneficiary = v2.beneficiary,
                status = v2.status,
                vetStaked = v2.vetStaked,
                validatorVetStaked = v2.validatorVetStaked,
                delegatorVetStaked = v2.delegatorVetStaked,
                queuedVetStaked = v2.queuedVetStaked,
                validatorQueuedVetStaked = v2.validatorQueuedVetStaked,
                delegatorQueuedVetStaked = v2.delegatorQueuedVetStaked,
                validatorExitingVetStaked = v2.validatorExitingVetStaked,
                delegatorExitingVetStaked = v2.delegatorExitingVetStaked,
                exitingVetStaked = v2.exitingVetStaked,
                cycleEndBlock = v2.cycleEndBlock,
                totalRewards = null,
                blockProbability = v2.blockProbability,
                blocksPerEpoch = BigDecimal.valueOf(v2.blocksPerEpoch),
                totalTvl = v2.totalTvl,
                validatorTvl = v2.validatorTvl,
                delegatorTvl = v2.delegatorTvl,
                validatorTvlPercentage = v2.validatorTvlPercentage,
                tvlBasedYield = v2.tvlBasedYield,
                validatorYield = v2.validatorYield,
                avgDelegatorYield = v2.avgDelegatorYield,
                nextCycleTvlBasedYield = v2.nextCycleTvlBasedYield,
                nextCycleValidatorYield = v2.nextCycleValidatorYield,
                nextCycleAvgDelegatorYield = v2.nextCycleAvgDelegatorYield,
                nftYieldsIfDelegatedNextCycle = v2.nftYieldsIfDelegatedNextCycle,
                nftYields = v2.nftYields,
                totalWeight = v2.totalWeight,
                online = null,
                completedPeriods = v2.completedPeriods,
                startBlock = v2.startBlock,
                cyclePeriodLength = v2.cyclePeriodLength,
                blocksPerYear = v2.blocksPerYear,
                percentageOffline = v2.percentageOffline,
                offlineBlocks = v2.missedBlocks,
                exitBlock = v2.exitBlock,
                queuePosition = v2.queuePosition,
                availableStartBlock = v2.availableStartBlock,
            )
        }
    }
}
