package org.vechain.indexer.api.controller

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest

class ContractControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = "/api/v1/contracts/"
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
        val result = mockMvc.get("${baseEndpoint}/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
            .andExpect { status { isOk() } }
            .andReturn()

        val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_TYPE)

        assert(contracts.size == 18)
    }

}