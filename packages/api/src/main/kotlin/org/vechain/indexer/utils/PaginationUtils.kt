package org.vechain.indexer.utils

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.springframework.data.domain.Sort.Direction
import org.vechain.indexer.constants.DEFAULT_PAGE_NUMBER
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.exception.BadRequestException

object PaginationUtils {

    fun toPageable(
        page: Int?,
        size: Int?,
        direction: String? = "desc",
        vararg fields: String = arrayOf("id")
    ): Pageable {
        return PageRequest.of(
            page ?: DEFAULT_PAGE_NUMBER,
            size ?: DEFAULT_PAGE_SIZE,
            Sort.by(toSortDirection(direction), *fields)
        )
    }

    private fun toSortDirection(direction: String?): Direction {
        val sortDirection: Direction
        try {
            sortDirection = Direction.fromString(direction ?: "desc")
        } catch (e: IllegalArgumentException) {
            throw BadRequestException("Invalid sort direction param: $direction")
        }
        return sortDirection
    }

}