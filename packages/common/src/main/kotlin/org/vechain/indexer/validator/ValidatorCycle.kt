package org.vechain.indexer.validator

import java.math.BigDecimal

/** The validator fields other indexers read, as `validator.cycle` held them at a block. */
data class ValidatorCycle(
    val id: String,
    val blockNumber: Long,
    val blockId: String,
    val status: Status?,
    val startBlock: Long?,
    val cyclePeriodLength: Long?,
    val exitBlock: Long?,
    val completedPeriods: Long?,
    val delegatorVetStaked: BigDecimal?,
)
