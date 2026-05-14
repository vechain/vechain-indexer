package org.vechain.indexer.validators

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.math.RoundingMode
import org.vechain.indexer.validator.StatusV2
import org.vechain.indexer.validator.ValidatorV2

/**
 * Public API representation of a [ValidatorV2] document.
 *
 * Carries every stored V2 field plus the **simple derivations** of V1 fields that can be computed
 * from a single document — `vetStaked`, queued / exiting splits, `cycleEndBlock`,
 * `percentageOffline`, and the network constants.
 *
 * Fields that V1 exposed but that need external data (current best block, chain-wide aggregates,
 * VET/VTHO prices, the reward ledger, or per-NFT delegation breakdowns) are documented in the
 * trailing comment block with the formula and dependency to wire up.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ValidatorV2Response(
    val id: String,
    val endorser: String?,
    val beneficiary: String?,
    val status: StatusV2?,

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

    // ---- Weight (raw, used by yield/probability formulas downstream) ----
    val validatorLockedWeight: BigDecimal?,
    val totalNextPeriodWeight: BigDecimal?,

    // ---- Cycle / queue position ----
    val startBlock: Long?,
    val exitBlock: Long?,
    val cyclePeriodLength: Long?,
    val cycleEndBlock: Long?,
    val completedPeriods: Long?,
    val queuePosition: Long?,
    val availableStartBlock: Long?,

    // ---- Liveness (PoS schedule attribution) ----
    val scheduledBlocks: Long,
    val proposedBlocks: Long,
    val missedBlocks: Long,
    val percentageOffline: BigDecimal?,
    val lastProposedBlockNumber: Long?,
    val lastMissedBlockNumber: Long?,

    // ---- Network constants (frozen, included for V1 response-shape parity) ----
    val blocksPerEpoch: Long = 180L,
    val blocksPerYear: Long = 3_155_760L,

    // =========================================================================
    // The following V1 fields are intentionally NOT populated yet. To add a
    // field, uncomment it on the data class, set it in `from(...)`, and wire
    // up the dependency named below.
    //
    // val online: Boolean?
    //   FORMULA: lastProposedBlockNumber != null &&
    //            (currentBestBlock - lastProposedBlockNumber) < ONLINE_RECENCY_THRESHOLD
    //   REQUIRES: ThorClient.getBlock(BlockRevision.Keyword.BEST) for currentBestBlock.
    //             Pick a threshold (suggested: 360 blocks = ~1 hour).
    //
    // val totalWeight: BigDecimal?
    //   FORMULA: sum(validatorLockedWeight) over status == ACTIVE at this block.
    //   REQUIRES: MongoTemplate aggregate ($match status=ACTIVE, $group sum=validatorLockedWeight)
    //             against `validators_v2`. Compute once per request and share across rows.
    //
    // val blockProbability: BigDecimal?
    //   FORMULA: validatorLockedWeight / totalWeight   (see `totalWeight` above).
    //
    // val totalRewards: BigDecimal?
    //   FORMULA: sum(reward) over the `validator_blocks` ledger entries for this validator.
    //   REQUIRES: ValidatorBlockRepository (V1's `validator-reward` profile). Decide whether
    //             V2 keeps using that ledger or rebuilds reward accounting independently.
    //             Note: V1's `Validator.totalRewards` was declared but never populated, so the
    //             ledger -> aggregate step is new work either way.
    //
    // val totalTvl: BigDecimal?
    // val validatorTvl: BigDecimal?
    // val delegatorTvl: BigDecimal?
    // val validatorTvlPercentage: BigDecimal?
    //   FORMULA: validatorTvl       = validatorVetStaked * vetPriceUsd
    //            delegatorTvl       = delegatorVetStaked * vetPriceUsd
    //            totalTvl           = validatorTvl + delegatorTvl
    //            validatorTvlPercentage = validatorTvl / totalTvl
    //   REQUIRES: VET/USD price. Keep PriceFeedOracle out of the indexer (it's network-specific);
    //             fetch at the API layer with caching, or stand up a separate price-sync source.
    //
    // val validatorYield: BigDecimal?
    // val tvlBasedYield: BigDecimal?
    // val avgDelegatorYield: BigDecimal?
    // val nextCycleValidatorYield: BigDecimal?
    // val nextCycleTvlBasedYield: BigDecimal?
    // val nextCycleAvgDelegatorYield: BigDecimal?
    //   FORMULA: see indexer's ValidatorCalculator.calculateValidatorYield. In short:
    //     vthoIssuedPerBlock = 1200 * 64 * sqrt(totalVetStaked) / 3_153_600
    //     annualIssuanceUsd  = blocksPerYear * blockProbability * vthoIssuedPerBlock * vthoPriceUsd
    //     validatorYield     = annualIssuanceUsd / validatorTvl *
    //                          (if hasDelegations then 0.3 else 1) * 100
    //     tvlBasedYield      = annualIssuanceUsd / validatorTvl *
    //                          (if hasDelegations then validatorTvl/totalTvl else 1) * 100
    //     avgDelegatorYield  = annualIssuanceUsd / delegatorTvl * 0.7 * 100   (only if
    // hasDelegations)
    //   The "nextCycle" variants substitute next-cycle stakes
    //     nextCycleValidatorStake  = validatorVetStaked + validatorQueuedVetStaked -
    // validatorExitingVetStaked
    //     nextCycleDelegationStake = delegatorVetStaked + delegatorQueuedVetStaked -
    // delegatorExitingVetStaked
    //   and use blocksPerYear(blockProbabilityNextCycle). Return zeros when
    //     status == EXITING && cycleEndBlock >= exitBlock (validator exits before the next period).
    //   REQUIRES: VET + VTHO USD prices, `totalWeight` (current and next cycle), per-validator
    //             current/next-cycle stake projections (formulas above), `hasDelegations` derived
    //             from `delegatorVetStaked > 0`.
    //
    // val nftYields: Map<TokenLevel, BigDecimal>?
    //   FORMULA: see ValidatorCalculator.calculateDelegatedNftLevelYieldsCurrentCycle.
    //   REQUIRES: DelegationRepository.aggregateActiveDelegationsByValidatorAndLevel()
    //             for the per-level NFT mix, VET + VTHO USD prices, TokenLevel.effectiveStake
    //             weights.
    //
    // val nftYieldsIfDelegatedNextCycle: Map<TokenLevel, BigDecimal>?
    //   FORMULA: see ValidatorCalculator.calculateNftLevelYieldsIfDelegatedNextCycle.
    //   REQUIRES: VET + VTHO USD prices, StarGate.getDelegatorsEffectiveStake(validator, period+2)
    //             (network-specific contract — fetch at the API layer with caching),
    //             TokenLevel weights, the 600 M VET MAX_VALIDATOR_STAKE cap.
    // =========================================================================
) {
    companion object {
        fun from(v: ValidatorV2): ValidatorV2Response {
            val validatorLocked = v.validatorLockedStake ?: BigDecimal.ZERO
            val delegatorsLocked = v.delegatorsLockedStake ?: BigDecimal.ZERO
            val totalLocked = validatorLocked + delegatorsLocked

            val totalQueued = v.totalQueuedStake ?: BigDecimal.ZERO
            val validatorQueued = v.validatorQueuedStake ?: BigDecimal.ZERO
            val delegatorQueued = (totalQueued - validatorQueued).max(BigDecimal.ZERO)

            val totalExiting = v.totalExitingStake ?: BigDecimal.ZERO
            val validatorExiting = v.validatorExitingStake ?: BigDecimal.ZERO
            val delegatorExiting = (totalExiting - validatorExiting).max(BigDecimal.ZERO)

            val startBlock = v.startBlock
            val completedPeriods = v.completedPeriods
            val stakingPeriodLength = v.stakingPeriodLength
            val cycleEndBlock =
                if (startBlock != null && completedPeriods != null && stakingPeriodLength != null) {
                    startBlock + (completedPeriods + 1L) * stakingPeriodLength
                } else null

            val percentageOffline =
                if (v.scheduledBlocks > 0L) {
                    BigDecimal(v.missedBlocks)
                        .divide(BigDecimal(v.scheduledBlocks), 6, RoundingMode.HALF_UP)
                        .multiply(BigDecimal(100))
                } else null

            return ValidatorV2Response(
                id = v.id,
                endorser = v.endorser,
                beneficiary = v.beneficiary,
                status = v.status,
                vetStaked = totalLocked.takeIf { it > BigDecimal.ZERO },
                validatorVetStaked = v.validatorLockedStake,
                delegatorVetStaked = v.delegatorsLockedStake,
                queuedVetStaked = v.totalQueuedStake,
                validatorQueuedVetStaked = v.validatorQueuedStake,
                delegatorQueuedVetStaked = delegatorQueued.takeIf { it > BigDecimal.ZERO },
                exitingVetStaked = v.totalExitingStake,
                validatorExitingVetStaked = v.validatorExitingStake,
                delegatorExitingVetStaked = delegatorExiting.takeIf { it > BigDecimal.ZERO },
                validatorLockedWeight = v.validatorLockedWeight,
                totalNextPeriodWeight = v.totalNextPeriodWeight,
                startBlock = v.startBlock,
                exitBlock = v.exitBlock,
                cyclePeriodLength = v.stakingPeriodLength,
                cycleEndBlock = cycleEndBlock,
                completedPeriods = v.completedPeriods,
                queuePosition = v.queuePosition,
                availableStartBlock = v.availableStartBlock,
                scheduledBlocks = v.scheduledBlocks,
                proposedBlocks = v.proposedBlocks,
                missedBlocks = v.missedBlocks,
                percentageOffline = percentageOffline,
                lastProposedBlockNumber = v.lastProposedBlockNumber,
                lastMissedBlockNumber = v.lastMissedBlockNumber,
            )
        }
    }
}
