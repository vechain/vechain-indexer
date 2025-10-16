package org.vechain.indexer.utils

import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.validator.Validator

object SortFieldUtils {
    fun getSortFieldValidator(sortBy: String): String =
        when {
            sortBy == "validatorTvl" -> Validator::validatorTvl.name
            sortBy == "totalTvl" -> Validator::totalTvl.name
            sortBy == "blockProbability" -> Validator::blockProbability.name
            sortBy == "delegatorTvl" -> Validator::delegatorTvl.name
            sortBy.startsWith("nft:") -> "nftYieldsNextCycle.${sortBy.removePrefix("nft:")}"
            else ->
                throw BadRequestException(
                    "Invalid sortBy value: $sortBy. Allowed values: validatorTvl, totalTvl, blockProbability, delegatorTvl, nft:<Level>."
                )
        }
}
