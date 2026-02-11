package org.vechain.indexer.utils

import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.stargate.token.TokenLevel
import org.vechain.indexer.validator.Validator

object SortFieldUtils {
    private val VALID_NFT_LEVELS =
        TokenLevel.entries.filter { it != TokenLevel.All }.map { it.name }.toSet()

    fun getSortFieldValidator(sortBy: String): String =
        when {
            sortBy == "validatorTvl" -> Validator::validatorTvl.name
            sortBy == "totalTvl" -> Validator::totalTvl.name
            sortBy == "blockProbability" -> Validator::blockProbability.name
            sortBy == "delegatorTvl" -> Validator::delegatorTvl.name
            sortBy.startsWith("nft:") -> {
                val level = sortBy.removePrefix("nft:")
                if (level !in VALID_NFT_LEVELS) {
                    throw BadRequestException(
                        "Invalid nft level: $level. Allowed values: ${VALID_NFT_LEVELS.joinToString(", ")}."
                    )
                }
                "nftYieldsNextCycle.$level"
            }
            else ->
                throw BadRequestException(
                    "Invalid sortBy value: $sortBy. Allowed values: validatorTvl, totalTvl, blockProbability, delegatorTvl, nft:<Level>."
                )
        }
}
