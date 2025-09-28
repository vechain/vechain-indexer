package org.vechain.indexer.validator.logic

import org.vechain.indexer.validator.Delegation

/**
 * Represents the in-memory state of validators during processing of a block cycle.
 * - Holds the current block context (id, number, timestamp).
 * - Holds the working set of validators (mutable).
 * - Mutations should update this state instead of writing directly to the DB.
 * - At the end of the cycle, this context is flushed to persistence.
 */
class DelegationContext(
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    private val _delegations: MutableMap<String, Delegation>,
    private val nextCycleResolver: ((String, Long) -> Pair<Long, Long>)? = null,
    private val validatorExitBlockResolver: ((String) -> Long)? = null,
) {
    val delegations: Map<String, Delegation>
        get() = _delegations

    /** Convenience accessor for getting a validator or throwing if missing. */
    fun requireDelegation(id: String): Delegation =
        _delegations[id] ?: throw IllegalStateException("Validator $id not found in context")

    /** Add or update a validator in the state. */
    fun put(delegation: Delegation) {
        _delegations[delegation.id] = delegation
    }

    /** Remove a validator from the state. */
    fun remove(validatorId: String) {
        _delegations.remove(validatorId)
    }

    /** Get a snapshot of all validators for persistence. */
    fun snapshot(): Map<String, Delegation> = _delegations.toMap()

    fun resolveNextCycle(validatorId: String, currentBlock: Long): Pair<Long, Long> =
        nextCycleResolver?.invoke(validatorId, currentBlock)
            ?: throw IllegalStateException("No resolver configured for nextCycleBlock")

    fun resolveValidatorExitBlock(validatorId: String): Long =
        validatorExitBlockResolver?.invoke(validatorId)
            ?: throw IllegalStateException("No resolver configured for nextCycleBlock")
}
