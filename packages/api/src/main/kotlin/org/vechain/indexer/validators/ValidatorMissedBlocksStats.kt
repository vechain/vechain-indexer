@file:Suppress("DEPRECATION")

package org.vechain.indexer.validators

/** Legacy response shape for the deprecated `/api/v1/validators/blocks/missed` endpoint. */
@Deprecated("Removed alongside /api/v1/validators/blocks/missed once clients migrate")
data class ValidatorMissedBlocksPercentage(val validator: String, val missedPercentage: Double)

@Deprecated("Removed alongside /api/v1/validators/blocks/missed once clients migrate")
data class AllValidatorsMissedBlocksResponse(
    val timeframe: MissedBlocksTimeframe,
    val startBlock: Long,
    val endBlock: Long,
    val validators: List<ValidatorMissedBlocksPercentage>,
)
