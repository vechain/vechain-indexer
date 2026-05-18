package org.vechain.indexer.utils

import org.vechain.indexer.exception.BadRequestException
import org.vechain.indexer.validator.Validator

object SortFieldUtils {
    /**
     * Translates a deprecated V1 `sortBy` value to a [Validator] field name. Price-multiplied
     * fields (TVL) collapse to their underlying stake field because vetPrice is a per-request
     * scalar — multiplying every row by the same constant preserves order. `blockProbability`
     * collapses to `validatorLockedWeight` for the same reason (totalWeight is the constant).
     *
     * `nft:<Level>` had no comparable persisted field on V2 and silently falls back to the default
     * sort field. The strict syntax check is dropped: V1 is deprecated and we'd rather degrade
     * than 400.
     */
    fun getSortFieldValidator(sortBy: String): String =
        when {
            sortBy == "validatorTvl" -> Validator::validatorVetStaked.name
            sortBy == "totalTvl" -> Validator::vetStaked.name
            sortBy == "blockProbability" -> Validator::validatorLockedWeight.name
            sortBy == "delegatorTvl" -> Validator::delegatorVetStaked.name
            sortBy.startsWith("nft:") -> Validator::validatorVetStaked.name
            else ->
                throw BadRequestException(
                    "Invalid sortBy value: $sortBy. Allowed values: validatorTvl, totalTvl, blockProbability, delegatorTvl, nft:<Level>."
                )
        }
}
