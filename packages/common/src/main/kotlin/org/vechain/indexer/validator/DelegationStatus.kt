package org.vechain.indexer.validator

/**
 * Lifecycle status of a [Delegation]. Deliberately separate from V1's [Status] enum so V2 is
 * isolated from V1, and from [StatusV2] which models validators (not delegations).
 *
 * Transitions:
 * - On [DelegationService] observing `DelegationInitiated` → [QUEUED]
 * - At validator's next cycle boundary while [QUEUED] → [ACTIVE]
 * - On `DelegationExitRequested` or `ValidationSignaledExit` for the bound validator → [EXITING]
 * - At validator's exit-cycle boundary while [EXITING], or on `DelegationWithdrawn` /
 *   `ValidationWithdrawn` → [EXITED]
 */
enum class DelegationStatus {
    QUEUED,
    ACTIVE,
    EXITING,
    EXITED,
}
