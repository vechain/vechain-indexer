package org.vechain.indexer.validator

/**
 * Mirrors the four states the built-in staker actually exposes (`Unknown`, `Queued`, `Active`,
 * `Exit`), plus one indexer-derived state:
 * - [EXITING] is not a separate on-chain status — it's chain-`Active` with a non-null `ExitBlock`.
 *   The indexer surfaces it as its own value so API consumers don't have to combine two fields.
 *
 * Once a validator hits [EXITED] on chain it stays there; there is no further terminal state. The
 * previously-defined `WITHDRAWN` value was modelling a transition the chain doesn't make and was
 * being set incorrectly — it has been removed.
 */
enum class Status {
    NONE,
    QUEUED,
    ACTIVE,
    EXITING,
    EXITED;

    companion object {
        fun fromCode(code: Int): Status =
            when (code) {
                0 -> NONE
                1 -> QUEUED
                2 -> ACTIVE
                3 -> EXITED
                else -> NONE
            }
    }
}
