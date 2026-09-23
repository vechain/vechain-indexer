package org.vechain.indexer.validator

data class ValidatorSnapshot(
    val validatorId: String,
    val stakingPeriodLength: Long,
    val startBlock: Long,
    val exitBlock: Long,
)
