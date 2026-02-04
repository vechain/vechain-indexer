package org.vechain.indexer.validator

import java.math.BigInteger

data class ValidatorLatestBlockResult(val _id: ValidatorId, val blockTimestamp: Long)

data class ValidatorId(val validator: String)

data class ValidatorRewardTotal(val total: BigInteger)
