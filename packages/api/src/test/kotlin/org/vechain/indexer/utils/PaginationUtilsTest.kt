package org.vechain.indexer.utils

import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.vechain.indexer.constants.DEFAULT_PAGE_NUMBER
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import strikt.api.expect
import strikt.assertions.isEqualTo

internal class PaginationUtilsTest {

    @Test
    fun `default page number when page is null`() {
        val page = null
        val size = 50

        val pageable = PaginationUtils.toPageable(page, size)

        expect {
            that(pageable.pageNumber).isEqualTo(DEFAULT_PAGE_NUMBER)
            that(pageable.pageSize).isEqualTo(size)
        }
    }

    @Test
    fun `default page size when size is null`() {
        val page = 1
        val size = null

        val pageable = PaginationUtils.toPageable(page, size)

        expect {
            that(pageable.pageNumber).isEqualTo(page)
            that(pageable.pageSize).isEqualTo(DEFAULT_PAGE_SIZE)
        }
    }

    @Test
    fun `default pageable when page & size are null`() {
        val page = null
        val size = null

        val pageable = PaginationUtils.toPageable(page, size)

        expect {
            that(pageable.pageNumber).isEqualTo(DEFAULT_PAGE_NUMBER)
            that(pageable.pageSize).isEqualTo(DEFAULT_PAGE_SIZE)
        }
    }

    @Test
    fun `pageable with non null page & size`() {
        val page = 13
        val size = 30

        val pageable = PaginationUtils.toPageable(page, size)

        expect {
            that(pageable.pageNumber).isEqualTo(page)
            that(pageable.pageSize).isEqualTo(size)
        }
    }

    @Test
    fun `pagination sorting default is by ascending id`() {
        val page = 13
        val size = 30

        val pageable = PaginationUtils.toPageable(page, size)

        expect {
            that(pageable.sort).isEqualTo(Sort.by(Sort.Direction.ASC, "id"))
        }
    }

    @Test
    fun `pagination custom sorting can be passed as param`() {
        val page = 13
        val size = 30
        val sort = Sort.by(Sort.Direction.ASC, "number")

        val pageable = PaginationUtils.toPageable(page, size, sort)

        expect {
            that(pageable.sort).isEqualTo(sort)
        }
    }
}