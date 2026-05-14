package org.vechain.indexer.validators

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.Delegation
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus
import org.vechain.indexer.validator.StatusV2
import org.vechain.indexer.validator.ValidatorV2
import org.vechain.indexer.validator.ValidatorV2Repository

/**
 * Per-request aggregates that feed [ValidatorV2Response.from]. Built once per HTTP request and
 * shared across every row, so list endpoints don't recompute the chain-wide sums per validator.
 * - [totalWeight]: chain-wide sum of `validatorLockedWeight` across ACTIVE validators. Drives
 *   `blockProbability` and the current-cycle yield formulas.
 * - [totalNextPeriodWeight]: chain-wide sum of `ValidatorV2.totalNextPeriodWeight` across ACTIVE
 *   validators. Drives `blockProbabilityNextCycle` and `nftYieldsIfDelegatedNextCycle`.
 * - [delegationsByValidator]: every relevant delegation (status QUEUED / ACTIVE / EXITING) grouped
 *   by validator id, for the per-validator current-cycle and next-cycle delegation calculations.
 */
data class ValidatorV2Aggregates(
    val totalWeight: BigDecimal,
    val totalNextPeriodWeight: BigDecimal,
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

/** Builds [ValidatorV2Aggregates] for one request. Active when the V2 validator profile is on. */
@Profile("validator-v2", "validator")
@Service
open class ValidatorV2AggregateService(
    private val validatorRepository: ValidatorV2Repository,
    private val delegationRepository: DelegationRepository,
) {
    /**
     * Compute the per-request aggregate context. Performs **three** Mongo round-trips total
     * regardless of page size:
     * 1. all ACTIVE validators (for the two chain-wide weight sums)
     * 2. relevant delegations for the validators we're about to render
     *
     * The active-validator query is the same one [ValidatorV2Service] uses for its in-memory cache;
     * it's small (~100 rows) and easily cacheable at the API layer if it ever shows up on profiles.
     */
    open fun build(validatorIdsOnPage: List<String>): ValidatorV2Aggregates {
        val activeValidators = validatorRepository.findByStatus(StatusV2.ACTIVE)
        val totalWeight = sumWeight(activeValidators) { it.validatorLockedWeight }
        val totalNextPeriodWeight = sumWeight(activeValidators) { it.totalNextPeriodWeight }

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

        return ValidatorV2Aggregates(
            totalWeight = totalWeight,
            totalNextPeriodWeight = totalNextPeriodWeight,
            delegationsByValidator = delegations.groupBy { it.validator },
        )
    }

    private fun sumWeight(
        validators: List<ValidatorV2>,
        selector: (ValidatorV2) -> BigDecimal?,
    ): BigDecimal =
        validators.fold(BigDecimal.ZERO) { acc, v -> acc + (selector(v) ?: BigDecimal.ZERO) }
}
