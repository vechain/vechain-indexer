package org.vechain.indexer.validator

/**
 * Per-validator slot accounting over a timestamp window. `missedSlotRatio` is `missedSlots /
 * (proposedBlocks + missedSlots)`, or `0.0` when the validator had no scheduled slots in the
 * window.
 */
data class ValidatorSlotStats(
    val validator: String,
    val proposedBlocks: Long,
    val missedSlots: Long,
    val missedSlotRatio: Double,
)
