package org.vechain.indexer.utils

import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.validator.Validator

object SortFieldUtils {
    /** Get the sort field for validators. */
    fun getSortFieldValidator(sortBy: String): String =
        when (sortBy) {
            "validatorTvl" -> Validator::validatorTvl.name
            "totalTvl" -> Validator::totalTvl.name
            "blockProbability" -> Validator::blockProbability.name
            else ->
                throw BadRequestException(
                    "Invalid sortBy value: $sortBy. Allowed values are: validatorTvl, totalTvl, blockProbability."
                )
        }
}
