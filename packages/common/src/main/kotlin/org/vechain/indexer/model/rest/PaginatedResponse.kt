package org.vechain.indexer.model.rest

import org.springframework.data.domain.Page

const val COUNT_LIMIT = 500L

data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationDetail? = null
) where T : Any

data class PaginationDetail(
    val isExactCount: Boolean,
    val countLimit: Long,
    val totalPages: Int? = null,
    val totalElements: Long? = null,
    val hasNext: Boolean,
)

/**
 * Builds a paginated API response based on a single results page.
 *
 * The count of response elements of the API request is limited to COUNT_LIMIT + 1,
 * in order to detect if the results number is above the configured count limit.
 *
 * If the count is less or equal to the COUNT_LIMIT, we return the exact count of elements/pages.
 * Otherwise, an exact count of elements/pages is not returned.
 *
 * All the response elements along with the count limit are still always returned,
 * and the hasNext flag provides an indication whether it's useful to keep querying more pages.
 */
fun <T : Any> paginatedResponse(page: Page<T>): PaginatedResponse<T> {
    val isExactCount = page.totalElements < COUNT_LIMIT + 1

    return PaginatedResponse(
        data = page.content,
        pagination = PaginationDetail(
            isExactCount = isExactCount,
            countLimit = COUNT_LIMIT,
            totalPages = if (isExactCount) page.totalPages else null,
            totalElements = if (isExactCount) page.totalElements else null,
            hasNext = page.hasNext()
        )
    )
}