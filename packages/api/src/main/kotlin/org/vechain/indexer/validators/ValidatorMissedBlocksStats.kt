package org.vechain.indexer.validators

data class ValidatorMissedBlocksPercentage(val validator: String, val missedPercentage: Double)

data class AllValidatorsMissedBlocksResponse(
    val timeframe: MissedBlocksTimeframe,
    val startBlock: Long,
    val endBlock: Long,
    val validators: List<ValidatorMissedBlocksPercentage>,
)
