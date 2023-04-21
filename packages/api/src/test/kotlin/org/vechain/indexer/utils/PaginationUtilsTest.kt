package org.vechain.indexer.utils

import org.junit.jupiter.api.Test
import org.springframework.data.domain.Sort
import org.vechain.indexer.constants.DEFAULT_PAGE_NUMBER
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.exception.BadRequestException
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure

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
    fun `pagination sorting default is by descending id`() {
        val page = 13
        val size = 30

        val pageable = PaginationUtils.toPageable(page, size)

        expect {
            that(pageable.sort).isEqualTo(Sort.by(Sort.Direction.DESC, "id"))
        }
    }

    @Test
    fun `pagination custom sorting can be passed as param`() {
        val page = 13
        val size = 30
        val direction = "desc"
        val fields = arrayOf("number")

        val pageable = PaginationUtils.toPageable(page, size, direction, *fields)

        expect {
            that(pageable.sort).isEqualTo(Sort.by(Sort.Direction.fromString(direction), *fields))
        }
    }

    @Test
    fun `should throw bad request exception for invalid sort direction param`() {
        val page = 13
        val size = 30
        val direction = "invalid"
        val fields = arrayOf("number")

        expect {
            catching { PaginationUtils.toPageable(page, size, direction, *fields) }
                .isFailure()
                .isA<BadRequestException>()
                .get(BadRequestException::message)
                .isEqualTo("Invalid sort direction param: $direction")
        }
    }
}