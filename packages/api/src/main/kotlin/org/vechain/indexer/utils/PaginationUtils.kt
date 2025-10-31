package org.vechain.indexer.utils

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.constants.DEFAULT_PAGE_NUMBER
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.constants.DEFAULT_SORT_DIRECTION
import org.vechain.indexer.constants.DEFAULT_SORT_FIELDS
import org.vechain.indexer.exception.BadRequestException

object PaginationUtils {

    private const val MAX_PAGE_NUMBER = 10_000

    fun toPageable(
        page: Int?,
        size: Int?,
        direction: String? = DEFAULT_SORT_DIRECTION,
        vararg fields: String = DEFAULT_SORT_FIELDS,
    ): Pageable {
        val pageNumber = page ?: DEFAULT_PAGE_NUMBER
        if (pageNumber < 0) {
            throw BadRequestException("Page index must be greater than or equal to zero")
        }
        if (pageNumber > MAX_PAGE_NUMBER) {
            throw BadRequestException("Page index must be less than or equal to $MAX_PAGE_NUMBER")
        }

        val pageSize = size ?: DEFAULT_PAGE_SIZE
        if (pageSize < 1) {
            throw BadRequestException("Page size must be greater than zero")
        }

        val requestedOffset = pageNumber.toLong() * pageSize.toLong()
        if (requestedOffset > Int.MAX_VALUE) {
            throw BadRequestException("Requested page is too large")
        }

        return PageRequest.of(pageNumber, pageSize, Sort.by(toSortDirection(direction), *fields))
    }

    private fun toSortDirection(direction: String?): Direction {
        val sortDirection: Direction
        try {
            sortDirection = Direction.fromString(direction ?: DEFAULT_SORT_DIRECTION)
        } catch (e: IllegalArgumentException) {
            throw BadRequestException("Invalid sort direction param: $direction")
        }
        return sortDirection
    }
}
