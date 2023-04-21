package org.vechain.indexer.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.CLAUSES_PATH
import org.vechain.indexer.model.WrappedClause
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isSorted

internal class ClauseControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = CLAUSES_PATH
        const val CLAUSES_TOTAL_NUMBER = 88
    }

    @Autowired
    lateinit var mockMvc: MockMvc


    @Test
    fun `get clauses with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get all clauses with valid endpoint should return OK`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, CLAUSES_TYPE)

        expectThat(clauses).hasSize(CLAUSES_TOTAL_NUMBER)
    }

    @Test
    fun `get all clauses with paginated result`() {
        val page = 4
        val size = 20
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, CLAUSES_TYPE)

        expectThat(clauses).hasSize(CLAUSES_TOTAL_NUMBER - (page * size))
    }

    @Test
    fun `get all clauses is sorted by blockNumber & txId & id`() {
        val page = 1
        val size = 20
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val clauses = objectMapper.readValue(result.response.contentAsString, CLAUSES_TYPE)

        expectThat(clauses).isSorted(
            compareByDescending<WrappedClause> { it.blockNumber }
                .then(compareByDescending<WrappedClause> { it.txId }
                    .then(compareByDescending { it.id })
                )
        )
    }

}