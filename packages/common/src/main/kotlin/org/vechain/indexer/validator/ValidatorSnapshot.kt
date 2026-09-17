package org.vechain.indexer.validator

import org.vechain.indexer.thor.model.BlockIdentifier

data class ValidatorSnapshot(
    val validatorId: String,
    val stakingPeriodLength: Long,
    val startBlock: Long,
    val exitBlock: Long,
)

/** A [ValidatorSnapshot] with the block that wrote it; `current` is false once superseded. */
data class ValidatorSnapshotRow(
    val block: BlockIdentifier,
    val current: Boolean,
    val snapshot: ValidatorSnapshot,
)
