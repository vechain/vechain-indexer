package org.vechain.indexer.validators

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorRepository

/**
 * Per-request aggregates that feed [ValidatorV2Response.from]. Built once per HTTP request and
 * shared across every row, so list endpoints don't recompute the chain-wide sums per validator.
 * - [totalWeight]: chain-wide sum of `validatorLockedWeight` across ACTIVE validators. Drives
 *   `blockProbability` and the current-cycle yield formulas.
 * - [totalNextPeriodWeight]: chain-wide sum of `Validator.totalNextPeriodWeight` across ACTIVE
 *   validators. Drives `blockProbabilityNextCycle` and `nftYieldsIfDelegatedNextCycle`.
 * - [totalActiveVetStaked]: chain-wide sum of `validatorVetStaked + delegatorVetStaked` across
 *   ACTIVE validators. Feeds the VeChain VTHO issuance formula (`1200 × 64 × sqrt(networkVET)`),
 *   which is a network-wide quantity — passing a single validator's stake here under-issues by
 *   `sqrt(networkVET / validatorVET)`.
 * - [totalActiveNextCycleVetStaked]: same idea for next-cycle projections, including queued and
 *   minus exiting stake.
 * - [delegationsByValidator]: every relevant delegation (status QUEUED / ACTIVE / EXITING) grouped
 *   by validator id, for the per-validator current-cycle and next-cycle delegation calculations.
 */
data class ValidatorAggregates(
    val totalWeight: BigDecimal,
    val totalNextPeriodWeight: BigDecimal,
    val totalActiveVetStaked: BigDecimal,
    val totalActiveNextCycleVetStaked: BigDecimal,
    val delegationsByValidator: Map<String, List<Delegation>>,
) {
    /**
     * Current-cycle effective delegation stake: sum of `tokenLevel.effectiveStake` over ACTIVE +
     * EXITING.
     */
    fun currentCycleEffectiveDelegationStake(validatorId: String): BigDecimal =
        delegationsByValidator[validatorId]
            ?.filter {
                it.status == DelegationStatus.ACTIVE || it.status == DelegationStatus.EXITING
            }
            ?.sumOf { it.tokenLevel.effectiveStake } ?: BigDecimal.ZERO

    /**
     * Projected effective delegation stake at [periodPlusTwoBlock] for [validatorId]. Mirrors what
     * `Stargate.getDelegatorsEffectiveStake(validator, period+2)` would return — derived from the
     * stored `transitionAtBlock` on each delegation.
     */
    fun nextCycleEffectiveDelegationStake(
        validatorId: String,
        periodPlusTwoBlock: Long,
    ): BigDecimal =
        delegationsByValidator[validatorId]
            ?.filter { willBeActiveAt(it, periodPlusTwoBlock) }
            ?.sumOf { it.tokenLevel.effectiveStake } ?: BigDecimal.ZERO

    /**
     * Count of currently-active delegations per [TokenLevel] for [validatorId]. Used to feed V1's
     * `calculateDelegatedNftLevelYieldsCurrentCycle`.
     */
    fun currentDelegatedLevelCounts(validatorId: String): Map<TokenLevel, Long> =
        delegationsByValidator[validatorId]
            ?.filter {
                it.status == DelegationStatus.ACTIVE || it.status == DelegationStatus.EXITING
            }
            ?.groupingBy { it.tokenLevel }
            ?.eachCount()
            ?.mapValues { (_, c) -> c.toLong() } ?: emptyMap()

    companion object {
        private fun willBeActiveAt(d: Delegation, atBlock: Long): Boolean {
            val transitionAt = d.transitionAtBlock
            return when (d.status) {
                DelegationStatus.ACTIVE -> true
                DelegationStatus.QUEUED -> transitionAt != null && transitionAt <= atBlock
                DelegationStatus.EXITING -> transitionAt != null && transitionAt > atBlock
                DelegationStatus.EXITED -> false
            }
        }
    }
}

/** Builds [ValidatorAggregates] for one request. Active when the V2 validator profile is on. */
@Profile("validator")
@Service
open class ValidatorAggregateService(
    private val validatorRepository: ValidatorRepository,
    private val delegationRepository: DelegationRepository,
) {
    /**
     * Compute the per-request aggregate context. Performs **three** Mongo round-trips total
     * regardless of page size:
     * 1. all ACTIVE validators (for the two chain-wide weight sums)
     * 2. relevant delegations for the validators we're about to render
     *
     * The active-validator query is the same one [ValidatorService] uses for its in-memory cache;
     * it's small (~100 rows) and easily cacheable at the API layer if it ever shows up on profiles.
     */
    open fun build(validatorIdsOnPage: List<String>): ValidatorAggregates {
        val activeValidators = validatorRepository.findByStatus(Status.ACTIVE)
        val totalWeight = sumWeight(activeValidators) { it.validatorLockedWeight }
        val totalNextPeriodWeight = sumWeight(activeValidators) { it.totalNextPeriodWeight }
        val totalActiveVetStaked =
            activeValidators.fold(BigDecimal.ZERO) { acc, v ->
                acc +
                    (v.validatorVetStaked ?: BigDecimal.ZERO) +
                    (v.delegatorVetStaked ?: BigDecimal.ZERO)
            }
        val totalActiveNextCycleVetStaked =
            activeValidators.fold(BigDecimal.ZERO) { acc, v ->
                val locked =
                    (v.validatorVetStaked ?: BigDecimal.ZERO) +
                        (v.delegatorVetStaked ?: BigDecimal.ZERO)
                val queued = v.queuedVetStaked ?: BigDecimal.ZERO
                val exiting = v.exitingVetStaked ?: BigDecimal.ZERO
                acc + (locked + queued - exiting).max(BigDecimal.ZERO)
            }

        val delegations =
            if (validatorIdsOnPage.isEmpty()) emptyList()
            else
                delegationRepository.findByValidatorInAndStatusIn(
                    validatorIdsOnPage,
                    listOf(
                        DelegationStatus.QUEUED,
                        DelegationStatus.ACTIVE,
                        DelegationStatus.EXITING,
                    ),
                )

        return ValidatorAggregates(
            totalWeight = totalWeight,
            totalNextPeriodWeight = totalNextPeriodWeight,
            totalActiveVetStaked = totalActiveVetStaked,
            totalActiveNextCycleVetStaked = totalActiveNextCycleVetStaked,
            delegationsByValidator = delegations.groupBy { it.validator },
        )
    }

    private fun sumWeight(
        validators: List<Validator>,
        selector: (Validator) -> BigDecimal?,
    ): BigDecimal =
        validators.fold(BigDecimal.ZERO) { acc, v -> acc + (selector(v) ?: BigDecimal.ZERO) }
}
