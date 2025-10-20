package org.vechain.indexer.validators

object ErrorMessages {
    const val ERROR_INVALID_START_AND_END_BLOCK_NUMBERS =
        "Invalid request: startBlockNumber must be less than or equal to endBlockNumber."

    const val ERROR_START_TIME_CANNOT_BE_NEGATIVE = "Invalid request: startTime cannot be negative."

    const val ERROR_END_TIME_CANNOT_BE_LESS_THAN_START_TIME =
        "Invalid request: endTime cannot be less than startTime."
}
