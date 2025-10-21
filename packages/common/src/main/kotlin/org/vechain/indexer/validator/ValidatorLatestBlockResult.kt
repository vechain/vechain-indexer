package org.vechain.indexer.validator

data class ValidatorLatestBlockResult(val _id: ValidatorId, val blockTimestamp: Long)

data class ValidatorId(val validator: String)
