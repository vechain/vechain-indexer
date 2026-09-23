package org.vechain.indexer.validator

/**
 * A validator's current delegations by status, from [DelegationReadRepository.countsByValidator].
 */
data class DelegationStatusCounts(
    val validator: String,
    val queued: Long,
    val active: Long,
    val exiting: Long,
)

/**
 * One bucket of [DelegationReadRepository.aggregateDelegationFacetsByValidators]: the delegations
 * of one validator sharing a `(status, tokenLevel, transitionAtBlock)`, as a count. Enum fields
 * stay strings so an unknown level is dropped at the boundary rather than failing the read.
 */
data class DelegationLevelFacet(
    val validator: String,
    val status: String,
    val tokenLevel: String,
    val transitionAtBlock: Long?,
    val count: Long,
)
