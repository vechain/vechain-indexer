package org.vechain.indexer.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.CLAUSES_PATH
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.model.IndexedClause
import org.vechain.indexer.model.rest.COUNT_LIMIT
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isSorted
import strikt.assertions.isTrue

internal class ClauseControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = CLAUSES_PATH
    }

    @Autowired
    lateinit var mockMvc: MockMvc


    @Test
    fun `get clauses with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get clauses for address`() {
        val address = "0x438d785fffd68dfed059c6380d9b0d07441e263b"
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$address" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expectThat(clauses.data).hasSize(20)
    }

    @Test
    fun `get clauses for address no hex prefix`() {
        val address = "438d785fffd68dfed059c6380d9b0d07441e263b"
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$address" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expectThat(clauses.data).hasSize(20)
    }

    @Test
    fun `get clauses for address upper case`() {
        val address = "0x438D785FFFD68dfed059c6380d9b0d07441E263B"
        val page = 0
        val size = PAGE_SIZE_LIMIT
        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$address" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expectThat(clauses.data).hasSize(20)
    }

    @Test
    fun `get all clauses for contract address with paginated result`() {
        val contractAddress = "0x438d785fffd68dfed059c6380d9b0d07441e263b"
        val page = 0
        val size = 4
        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$contractAddress" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expectThat(clauses.data).hasSize(size)
    }

    @Test
    fun `get all clauses for origin`() {
        val origin = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
        val page = 1
        val size = 10
        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$origin" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expectThat(clauses.data)
            .isSorted(
                compareByDescending<IndexedClause> { it.blockNumber }
                    .then(compareByDescending<IndexedClause> { it.txId }
                        .then(compareByDescending { it.id })
                    )
            )
    }

    @Test
    fun `get all clauses for origin is paginated & sorted by default`() {
        val origin = "0x438d785fffd68dfed059c6380d9b0d07441e263b"

        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$origin"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expectThat(clauses.data)
            .hasSize(DEFAULT_PAGE_SIZE)
            .isSorted(
                compareByDescending<IndexedClause> { it.blockNumber }
                    .then(compareByDescending<IndexedClause> { it.txId }
                        .then(compareByDescending { it.id })
                    )
            )
    }

    @Test
    fun `get all clauses for origin with pagination & sorting & pagination detail`() {
        val origin = "0x438d785fffd68dfed059c6380d9b0d07441e263b"
        val page = 0
        val size = 10
        val result = mockMvc.get(
            baseEndpoint +
                    "?address=$origin" +
                    "&page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, PAGINATED_CLAUSES_TYPE)

        expect {
            that(clauses.data)
                .hasSize(size)
                .isSorted(
                    compareByDescending<IndexedClause> { it.blockNumber }
                        .then(compareByDescending<IndexedClause> { it.txId }
                            .then(compareByDescending { it.id })
                        )
                )

            that(clauses.pagination.isExactCount).isTrue()
            that(clauses.pagination.countLimit).isEqualTo(COUNT_LIMIT)
            that(clauses.pagination.totalElements).isEqualTo(20)
            that(clauses.pagination.totalPages).isEqualTo(2)
            that(clauses.pagination.hasNext).isTrue()
        }
    }

}