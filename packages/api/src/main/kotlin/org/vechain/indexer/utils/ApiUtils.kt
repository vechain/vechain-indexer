package org.vechain.indexer.utils

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort
import org.vechain.indexer.constants.DEFAULT_PAGE
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE

object ApiUtils {

    fun toPageable(page: Int?, size: Int?, sort: Sort = byAscendingId()): Pageable {
        return PageRequest.of(
            page ?: DEFAULT_PAGE,
            size ?: DEFAULT_PAGE_SIZE,
            sort
        )
    }

    private fun byAscendingId() = Sort.by(Sort.Direction.ASC, "id")

}