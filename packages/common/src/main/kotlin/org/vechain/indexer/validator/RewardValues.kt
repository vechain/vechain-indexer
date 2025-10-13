package org.vechain.indexer.validator

import java.math.BigInteger

data class RewardValues(
    val blockReward: BigInteger,
    val priorityReward: BigInteger,
    val total: BigInteger,
)
