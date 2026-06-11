package org.vechain.indexer.validators

import java.math.BigDecimal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.DelegationLevelFacet
import org.vechain.indexer.validator.DelegationRepository
import org.vechain.indexer.validator.DelegationStatus

/**
 * Per-request aggregates that feed [ValidatorV2Response.from]. Built once per HTTP request and
 * shared across every row, so list endpoints don't recompute the chain-wide sums per validator.
 * - [totalWeight]: chain-wide sum of `validatorLockedWeight` across ACTIVE validators. Drives
 *   `blockProbability` and the current-cycle yield formulas.
 * - [totalNextPeriodWeight]: chain-wide sum of `Validator.totalNextPeriodWeight` across the
 *   next-cycle active set (ACTIVE + QUEUED + EXITING — anything not terminally exited). Drives
 *   `blockProbabilityNextCycle` and `nftYieldsIfDelegatedNextCycle`. Including QUEUED here matters:
 *   queued-validator rows compute their next-cycle share against this denominator, so excluding
 *   them would over-state their own share.
 * - [totalActiveVetStaked]: chain-wide sum of `validatorVetStaked + delegatorVetStaked` across
 *   ACTIVE validators. Feeds the VeChain VTHO issuance formula (`1200 × 64 × sqrt(networkVET)`),
 *   which is a network-wide quantity — passing a single validator's stake here under-issues by
 *   `sqrt(networkVET / validatorVET)`.
 * - [totalActiveNextCycleVetStaked]: same idea for next-cycle issuance projections, summed across
 *   the next-cycle active set with each validator's `locked + queued - exiting`. Sourced from the
 *   same broader population as [totalNextPeriodWeight].
 * - [delegationFacetsByValidator]: counted facets of every relevant delegation (status QUEUED /
 *   ACTIVE / EXITING) grouped by validator id. Each facet is one bucket of identically- keyed
 *   delegations `(status, tokenLevel, transitionAtBlock)` with a count; the per-validator
 *   current-cycle and next-cycle delegation aggregates derive from these counts × the constant
 *   per-[TokenLevel] `effectiveStake`. Server-side aggregation avoids streaming every delegation
 *   row over the wire.
 */
data class ValidatorAggregates(
    val totalWeight: BigDecimal,
    val totalNextPeriodWeight: BigDecimal,
    val totalActiveVetStaked: BigDecimal,
    val totalActiveNextCycleVetStaked: BigDecimal,
    val delegationFacetsByValidator: Map<String, List<ParsedDelegationFacet>>,
) {
    /**
     * Current-cycle effective delegation stake: sum of `tokenLevel.effectiveStake` over ACTIVE +
     * EXITING.
     */
    fun currentCycleEffectiveDelegationStake(validatorId: String): BigDecimal =
        delegationFacetsByValidator[validatorId]?.let { facets ->
            facets.fold(BigDecimal.ZERO) { acc, f ->
                if (f.status == DelegationStatus.ACTIVE || f.status == DelegationStatus.EXITING)
                    acc + f.tokenLevel.effectiveStake.multiply(BigDecimal.valueOf(f.count))
                else acc
            }
        } ?: BigDecimal.ZERO

    /**
     * Projected effective delegation stake at [periodPlusTwoBlock] for [validatorId]. Mirrors what
     * `Stargate.getDelegatorsEffectiveStake(validator, period+2)` would return — derived from the
     * stored `transitionAtBlock` on each delegation.
     */
    fun nextCycleEffectiveDelegationStake(
        validatorId: String,
        periodPlusTwoBlock: Long,
    ): BigDecimal =
        delegationFacetsByValidator[validatorId]?.let { facets ->
            facets.fold(BigDecimal.ZERO) { acc, f ->
                if (willBeActiveAt(f, periodPlusTwoBlock))
                    acc + f.tokenLevel.effectiveStake.multiply(BigDecimal.valueOf(f.count))
                else acc
            }
        } ?: BigDecimal.ZERO

    /**
     * Count of currently-active delegations per [TokenLevel] for [validatorId]. Used to feed V1's
     * `calculateDelegatedNftLevelYieldsCurrentCycle`.
     */
    fun currentDelegatedLevelCounts(validatorId: String): Map<TokenLevel, Long> {
        val facets = delegationFacetsByValidator[validatorId] ?: return emptyMap()
        val counts = mutableMapOf<TokenLevel, Long>()
        for (f in facets) {
            if (f.status == DelegationStatus.ACTIVE || f.status == DelegationStatus.EXITING) {
                counts.merge(f.tokenLevel, f.count, Long::plus)
            }
        }
        return counts
    }

    companion object {
        private fun willBeActiveAt(f: ParsedDelegationFacet, atBlock: Long): Boolean {
            val transitionAt = f.transitionAtBlock
            return when (f.status) {
                DelegationStatus.ACTIVE -> true
                DelegationStatus.QUEUED -> transitionAt != null && transitionAt <= atBlock
                DelegationStatus.EXITING -> transitionAt != null && transitionAt > atBlock
                DelegationStatus.EXITED -> false
            }
        }
    }
}

/**
 * Enum-resolved view of [DelegationLevelFacet]. The repository returns the enum fields as `String`
 * to keep Spring Data MongoDB aggregation deserialization predictable; this type is the in-memory
 * form consumed by [ValidatorAggregates]. Facets whose `tokenLevel` or `status` doesn't parse to a
 * known enum value are dropped at the boundary in [ValidatorAggregateService.build].
 */
data class ParsedDelegationFacet(
    val status: DelegationStatus,
    val tokenLevel: TokenLevel,
    val transitionAtBlock: Long?,
    val count: Long,
)

/** Builds [ValidatorAggregates] for one request. Active when the V2 validator profile is on. */
@Profile("validator")
@Service
open class ValidatorAggregateService(
    private val chainAggregatesService: ValidatorChainAggregatesService,
    private val delegationRepository: DelegationRepository,
) {
    /**
     * Compute the per-request aggregate context.
     *
     * The chain-wide four-field summary (totalWeight, totalNextPeriodWeight, totalActiveVetStaked,
     * totalActiveNextCycleVetStaked) is fetched via [ValidatorChainAggregatesService] which caches
     * the result — no per-request validator fetch + fold pass when the cache is warm.
     *
     * Per-request work that remains: one Mongo round-trip to
     * `aggregateDelegationFacetsByValidators` for the validators on the page (count-buckets grouped
     * by `(status, tokenLevel, transitionAtBlock)`, not raw rows).
     */
    open fun build(validatorIds: List<String>): ValidatorAggregates {
        val chain = chainAggregatesService.get()

        val delegationFacets =
            if (validatorIds.isEmpty()) emptyList()
            else delegationRepository.aggregateDelegationFacetsByValidators(validatorIds)

        val facetsByValidator =
            delegationFacets
                .groupBy { it.validator }
                .mapValues { (_, raws) -> raws.mapNotNull { it.parsed() } }

        return ValidatorAggregates(
            totalWeight = chain.totalWeight,
            totalNextPeriodWeight = chain.totalNextPeriodWeight,
            totalActiveVetStaked = chain.totalActiveVetStaked,
            totalActiveNextCycleVetStaked = chain.totalActiveNextCycleVetStaked,
            delegationFacetsByValidator = facetsByValidator,
        )
    }

    private fun DelegationLevelFacet.parsed(): ParsedDelegationFacet? {
        val parsedStatus =
            runCatching { DelegationStatus.valueOf(status) }.getOrNull() ?: return null
        val parsedLevel = TokenLevel.fromString(tokenLevel) ?: return null
        return ParsedDelegationFacet(parsedStatus, parsedLevel, transitionAtBlock, count)
    }
}
