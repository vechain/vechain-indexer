package org.vechain.indexer.validator.models

import java.math.BigInteger

data class DecodedValidatorRow(
    val id: String,
    val endorser: String,
    val status: BigInteger,
    val online: Boolean,
    val offlineBlock: BigInteger,
    val stakingPeriodLength: Int,
    val startBlock: BigInteger,
    val exitBlock: BigInteger,
    val completedPeriods: BigInteger,
    val validatorLockedVET: BigInteger,
    val validatorLockedWeight: BigInteger,
    val delegatorsStake: BigInteger,
    val validatorQueuedStake: BigInteger,
    val totalQueuedStake: BigInteger,
    val totalExitingStake: BigInteger,
    val totalNextPeriodWeight: BigInteger,
    val nextPeriodDelegationStake: BigInteger,
)
