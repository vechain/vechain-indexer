package org.vechain.indexer.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.BLOCKS_PATH
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isSorted

internal class BlockControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = BLOCKS_PATH
        const val BLOCKS_TOTAL_NUMBER = 6
    }

    @Autowired
    lateinit var mockMvc: MockMvc


    @Test
    fun `get blocks with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get all blocks with valid endpoint should return OK`() {
        val page = 0
        val size = Int.MAX_VALUE
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val blocks = objectMapper.readValue(result.response.contentAsString, BLOCKS_TYPE)

        expectThat(blocks).hasSize(BLOCKS_TOTAL_NUMBER)
    }

    @Test
    fun `get all blocks with paginated result`() {
        val page = 1
        val size = 3
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val blocks = objectMapper.readValue(result.response.contentAsString, BLOCKS_TYPE)

        expectThat(blocks).hasSize(BLOCKS_TOTAL_NUMBER - (page * size))
    }

    @Test
    fun `get all blocks is sorted by block number`() {
        val page = 1
        val size = 3
        val result = mockMvc.get(
            baseEndpoint +
                    "?page=$page" +
                    "&size=$size"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val blocks = objectMapper.readValue(result.response.contentAsString, BLOCKS_TYPE)

        expectThat(blocks).isSorted(compareBy { it.blockNumber })
    }

}