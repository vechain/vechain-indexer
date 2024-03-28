package org.vechain.indexer.model.rest

import com.fasterxml.jackson.annotation.JsonView
import org.springframework.data.domain.Slice
import org.vechain.indexer.thor.model.Views

/** An API query can return at most this number of elements per page */
const val PAGE_SIZE_LIMIT = 150

/** An API query can count at most this number of elements per response */
const val COUNT_LIMIT = 500L

/** API response wrapper object */
@JsonView(Views.Public::class)
data class PaginatedResponse<T>(
    val data: List<T>,
    val pagination: PaginationDetail,
) where T : Any

/** Wrapper that holds pagination data inside a response */
@JsonView(Views.Public::class)
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
 * hasNext flag provides an indication whether it's useful to keep querying for more pages.
 */
fun <T : Any> paginatedResponse(slice: Slice<T>): PaginatedResponse<T> {
    return PaginatedResponse(
        data = slice.content,
        pagination =
            PaginationDetail(
                // Count fields deprecated, kept for backwards compatibility only temporarily
                hasCount = false,
                countLimit = 0,
                totalPages = null,
                totalElements = null,
                hasNext = slice.hasNext()
            )
    )
}
