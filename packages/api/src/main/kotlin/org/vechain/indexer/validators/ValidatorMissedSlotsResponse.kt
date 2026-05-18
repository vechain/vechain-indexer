package org.vechain.indexer.validators

import org.vechain.indexer.validator.ValidatorMissedSlotsResult

data class ValidatorMissedSlotsResponse(
    val timeframe: MissedBlocksTimeframe,
    val startBlock: Long,
    val endBlock: Long,
    val validators: List<ValidatorMissedSlotsResult>,
)
