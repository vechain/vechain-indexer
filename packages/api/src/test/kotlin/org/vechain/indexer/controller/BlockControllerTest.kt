package org.vechain.indexer.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.BLOCKS_PATH
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.util.*

internal class BlockControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = BLOCKS_PATH
    }

    @Autowired
    lateinit var mockMvc: MockMvc


    @Test
    fun `get blocks with path param should return NOT_FOUND`() {
        mockMvc.get("$baseEndpoint/pathParam")
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get block by number should return OK`() {
        val blockNumber = 1L
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockNumber"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val block = objectMapper.readValue(result.response.contentAsString, BLOCK_TYPE)

        expectThat(block.blockNumber).isEqualTo(blockNumber)
    }

    @Test
    fun `get block by invalid number should return 404`() {
        val blockNumber = "23456789345"
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockNumber"
        )
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get block by ID should return OK`() {
        val blockId = "0x000000040ec070526cdd2405b0c1653e0431c20774263e6681eeb541103d8e95"
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockId"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val block = objectMapper.readValue(result.response.contentAsString, BLOCK_TYPE)

        expectThat(block.blockId).isEqualTo(blockId)
    }

    @Test
    fun `get block by ID not lowercase should return OK`() {
        val blockId = "0x000000040EC070526cDd2405b0c1653e0431c20774263e6681eEb541103d8E95"
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockId"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val block = objectMapper.readValue(result.response.contentAsString, BLOCK_TYPE)

        expectThat(block.blockId).isEqualTo(blockId.lowercase(Locale.getDefault()))
    }

    @Test
    fun `get block by ID no hex prefix should return OK`() {
        val blockId = "000000040EC070526cDd2405b0c1653e0431c20774263e6681eEb541103d8E95"
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockId"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val block = objectMapper.readValue(result.response.contentAsString, BLOCK_TYPE)

        expectThat(block.blockId).isEqualTo("0x" + blockId.lowercase(Locale.getDefault()))
    }

    @Test
    fun `get block by ID that doesn't exists should return 404`() {
        val blockId = "0x00000008de120e47e15edb8d9a23823b198590623c3c00000000000000000000"
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockId"
        )
            .andExpect { status { isNotFound() } }
    }

    @Test
    fun `get block by invalid ID should return 400`() {
        val blockId = "0x00000008de12"
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=$blockId"
        )
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `get best block should return OK`() {
        val result = mockMvc.get(
            baseEndpoint +
                    "?revision=best"
        )
            .andExpect { status { isOk() } }
            .andReturn()

        val block = objectMapper.readValue(result.response.contentAsString, BLOCK_TYPE)

        expectThat(block.blockId).isEqualTo("0x00000008de120e47e15edb8d9a23823b198590623c3c9f938c5f623f13e7402a")
    }

}