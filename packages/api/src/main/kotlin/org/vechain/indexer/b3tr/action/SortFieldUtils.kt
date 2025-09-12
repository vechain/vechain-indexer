package org.vechain.indexer.b3tr.action

import org.vechain.indexer.exception.BadRequestException

object SortFieldUtils {
    fun assertSortFields(field: String, vararg allowedFields: String) {
        if (field !in allowedFields) {
            error(
                "Invalid sort field: $field. Allowed fields are: ${allowedFields.joinToString(", ")}"
            )
        }
    }

    /**
     * Get the sort field based on the sortBy parameter.
     *
     * @param sortBy The field to sort by.
     * @return The field to sort by.
     */
    fun getSortFieldUserAllTimeActionSummary(sortBy: String): String =
        when (sortBy) {
            "totalRewardAmount" -> UserAllTimeActionSummary::totalRewardAmount.name
            "actionsRewarded" -> UserAllTimeActionSummary::actionsRewarded.name
            else ->
                throw BadRequestException(
                    "Invalid sortBy value: $sortBy. Allowed values are: totalRewardAmount, actionsRewarded."
                )
        }

    fun getSortFieldUserRoundActionSummary(sortBy: String): String =
        when (sortBy) {
            "totalRewardAmount" -> UserRoundActionSummary::totalRewardAmount.name
            "actionsRewarded" -> UserRoundActionSummary::actionsRewarded.name
            "roundId" -> UserRoundActionSummary::roundId.name
            else ->
                throw BadRequestException(
                    "Invalid sortBy value: $sortBy. Allowed values are: roundId, totalRewardAmount, actionsRewarded."
                )
        }
}
