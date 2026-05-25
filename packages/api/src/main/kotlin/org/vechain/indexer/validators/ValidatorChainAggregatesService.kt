package org.vechain.indexer.validators

import java.math.BigDecimal
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Service
import org.vechain.indexer.validator.Status
import org.vechain.indexer.validator.Validator
import org.vechain.indexer.validator.ValidatorRepository

/**
 * Chain-wide aggregates over the next-cycle active set — totals that are independent of the page
 * being rendered. Used by [ValidatorAggregateService] to fill the four chain-wide fields of
 * [ValidatorAggregates] without re-fetching the same ~140 validators on every request.
 */
data class ChainWideValidatorAggregates(
    val totalWeight: BigDecimal,
    val totalNextPeriodWeight: BigDecimal,
    val totalActiveVetStaked: BigDecimal,
    val totalActiveNextCycleVetStaked: BigDecimal,
)

/**
 * Caches the chain-wide validator aggregates. One round-trip + four BigDecimal folds across the
 * ~140-validator active set fire at most once per cache TTL, regardless of request rate. Lives in a
 * separate class from [ValidatorAggregateService] so Spring's CGLIB proxy can intercept the
 * `@Cacheable` call (self-calls inside a single bean bypass the proxy).
 */
@Profile("validator")
@Service
open class ValidatorChainAggregatesService(private val validatorRepository: ValidatorRepository) {

    /**
     * Computes (and caches) the chain-wide aggregates. `sync = true` guarantees that on a miss only
     * one thread does the work; concurrent callers wait on the same future. The cache key is a
     * constant — this is the only entry the cache ever holds.
     */
    @Cacheable(value = [CHAIN_WIDE_AGGREGATES_CACHE], key = "'all'", sync = true)
    open fun get(): ChainWideValidatorAggregates {
        val nextCycleValidators =
            validatorRepository.findByStatusIn(listOf(Status.ACTIVE, Status.QUEUED, Status.EXITING))
        val activeValidators = nextCycleValidators.filter { it.status == Status.ACTIVE }

        val totalWeight = sumWeight(activeValidators) { it.validatorLockedWeight }
        val totalActiveVetStaked =
            activeValidators.fold(BigDecimal.ZERO) { acc, v ->
                acc +
                    (v.validatorVetStaked ?: BigDecimal.ZERO) +
                    (v.delegatorVetStaked ?: BigDecimal.ZERO)
            }

        val totalNextPeriodWeight = sumWeight(nextCycleValidators) { it.totalNextPeriodWeight }
        val totalActiveNextCycleVetStaked =
            nextCycleValidators.fold(BigDecimal.ZERO) { acc, v ->
                val locked =
                    (v.validatorVetStaked ?: BigDecimal.ZERO) +
                        (v.delegatorVetStaked ?: BigDecimal.ZERO)
                val queued = v.queuedVetStaked ?: BigDecimal.ZERO
                val exiting = v.exitingVetStaked ?: BigDecimal.ZERO
                acc + (locked + queued - exiting).max(BigDecimal.ZERO)
            }

        return ChainWideValidatorAggregates(
            totalWeight = totalWeight,
            totalNextPeriodWeight = totalNextPeriodWeight,
            totalActiveVetStaked = totalActiveVetStaked,
            totalActiveNextCycleVetStaked = totalActiveNextCycleVetStaked,
        )
    }

    private fun sumWeight(
        validators: List<Validator>,
        selector: (Validator) -> BigDecimal?,
    ): BigDecimal =
        validators.fold(BigDecimal.ZERO) { acc, v -> acc + (selector(v) ?: BigDecimal.ZERO) }

    companion object {
        const val CHAIN_WIDE_AGGREGATES_CACHE = "validator_chain_wide_aggregates"
    }
}
