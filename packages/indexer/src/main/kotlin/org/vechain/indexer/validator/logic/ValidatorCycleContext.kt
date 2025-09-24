package org.vechain.indexer.validator.logic

import org.vechain.indexer.validator.Validator

/**
 * Represents the in-memory state of validators during processing of a block cycle.
 * - Holds the current block context (id, number, timestamp).
 * - Holds the working set of validators (mutable).
 * - Mutations should update this state instead of writing directly to the DB.
 * - At the end of the cycle, this context is flushed to persistence.
 */
class ValidatorCycleContext(
    val blockId: String,
    val blockNumber: Long,
    val blockTimestamp: Long,
    private val _validators: MutableMap<String, Validator>,
) {
    val validators: Map<String, Validator>
        get() = _validators

    /** Convenience accessor for getting a validator or throwing if missing. */
    fun requireValidator(id: String): Validator =
        _validators[id] ?: throw IllegalStateException("Validator $id not found in context")

    /** Add or update a validator in the state. */
    fun put(validator: Validator) {
        _validators[validator.id] = validator
    }

    /** Remove a validator from the state. */
    fun remove(validatorId: String) {
        _validators.remove(validatorId)
    }

    /** Get a snapshot of all validators for persistence. */
    fun snapshot(): List<Validator> = _validators.values.toList()
}
