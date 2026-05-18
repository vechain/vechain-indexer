package org.vechain.indexer.validator

/**
 * Per-validator slot accounting over a block range.
 *
 * `scheduledSlots = proposedBlocks + missedSlots` and is the denominator for both ratios; ratios
 * are 0.0 for validators with no scheduled slots in the window.
 */
data class ValidatorMissedSlotsResult(
    val validator: String,
    val scheduledSlots: Long,
    val missedSlots: Long,
    val proposedBlocks: Long,
    val missedSlotRatio: Double,
    val livenessRatio: Double,
)
