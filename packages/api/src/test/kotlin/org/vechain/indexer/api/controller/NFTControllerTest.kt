package org.vechain.indexer.api.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest

class NFTControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = "/api/v1/nfts/"
    }

    @Autowired
    lateinit var mockMvc: MockMvc


    @Test
    fun `get transactions for bad address should return BAD_REQUEST`() {
        mockMvc.get("${baseEndpoint}/badAddress")
            .andExpect { status { isBadRequest() } }
    }

    @Test
    fun `valid address should return OKAY`() {
        val res = mockMvc.get("${baseEndpoint}/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
            .andExpect { status { isOk() } }
            .andReturn()

        val nfts = objectMapper.readValue(res.response.contentAsString, NFT_TYPE)

        assert(nfts.size == 2)
    }

}