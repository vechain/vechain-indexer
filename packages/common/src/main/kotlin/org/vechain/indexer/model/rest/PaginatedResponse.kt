package org.vechain.indexer.model.rest

import org.springframework.data.domain.Page

/**
 * An API query can return at most this number of elements per page
 */
const val PAGE_SIZE_LIMIT = 150

/**
 * An API query can count at most this number of elements per response
 */
const val COUNT_LIMIT = 500L

/**
 * API response wrapper object
 */
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationDetail,
) where T : Any

/**
 * Wrapper that holds pagination data inside a response
 */
data class PaginationDetail(
    val hasCount: Boolean,
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
    val hasCount = page.totalElements < COUNT_LIMIT + 1

    return PaginatedResponse(
        data = page.content,
        pagination = PaginationDetail(
            hasCount = hasCount,
            countLimit = COUNT_LIMIT,
            totalPages = if (hasCount) page.totalPages else null,
            totalElements = if (hasCount) page.totalElements else null,
            hasNext = page.hasNext()
        )
    )
}