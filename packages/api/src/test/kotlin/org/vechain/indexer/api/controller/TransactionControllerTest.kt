package org.vechain.indexer.api.controller

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest

class TransactionControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = "/api/v1/transactions/"
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Nested
    inner class OriginTransactions {
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

            val transactions = objectMapper.readValue(result.response.contentAsString, TX_TYPE)

            assert(transactions.size == 39)
        }

        @Test
        fun `include delegated should return 1 more`() {
            val result = mockMvc.get("${baseEndpoint}/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?includeDelegated=true")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, TX_TYPE)

            assert(transactions.size == 40)
        }

        @Test
        fun `include delegated false`() {
            val result =
                mockMvc.get("${baseEndpoint}/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa?includeDelegated=false")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, TX_TYPE)

            assert(transactions.size == 39)
        }
    }


    @Nested
    inner class DelegatedTransactions {

        @Test
        fun `get DELEGATED transactions for bad address should return BAD_REQUEST`() {
            mockMvc.get("${baseEndpoint}/badAddress/delegated")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get DELEGATED transactions should return OKAY`() {
            val result = mockMvc.get("${baseEndpoint}/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa/delegated")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, TX_TYPE)

            assert(transactions.size == 1)
        }
    }
}