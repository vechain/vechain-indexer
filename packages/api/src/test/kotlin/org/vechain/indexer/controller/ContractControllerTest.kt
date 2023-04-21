package org.vechain.indexer.controller

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.CONTRACTS_PATH
import org.vechain.indexer.model.Contract
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

internal class ContractControllerTest : AbstractIntegrationTest() {

    companion object {
        const val BASE_ENDPOINT = CONTRACTS_PATH
        const val TOTAL_CONTRACTS_NUMBER = 18
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Nested
    inner class ValidateContractsQueries {
        @Test
        fun `get transactions for bad address should return BAD_REQUEST`() {
            mockMvc.get("$BASE_ENDPOINT/badAddress")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `valid address should return OKAY`() {
            val result = mockMvc.get("$BASE_ENDPOINT/0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER)
        }

        @Test
        fun `valid address UPPERCASE`() {
            val result = mockMvc.get("$BASE_ENDPOINT/0xF077B491B355E64048CE21E3A6FC4751EEEa77FA")
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER)
        }

        @Test
        fun `mixed case`() {
            val result = mockMvc.get("$BASE_ENDPOINT/0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER)
        }

        @Test
        fun `no prefix hex`() {
            val result = mockMvc.get("$BASE_ENDPOINT/f077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER)
        }
    }

    @Nested
    inner class PaginateContractsQueries {
        @Test
        fun `fetch contracts by origin - no pagination`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = ""
            val size = ""
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER)
        }

        @Test
        fun `fetch contracts by origin - paginated search - page only with results`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = ""
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER)
        }

        @Test
        fun `fetch contracts by origin - paginated search - page only without results`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val size = ""
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).isEmpty()
        }

        @Test
        fun `fetch contracts by origin - paginated search - size only`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = ""
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(size)
        }

        @Test
        fun `fetch contracts by origin - paginated search - page & size`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!).hasSize(TOTAL_CONTRACTS_NUMBER - (page * size))
        }

        @Test
        fun `fetch contracts by origin - paginated search - sorted by blockNumber & txId & address`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expectThat(contracts.data!!)
                .hasSize(TOTAL_CONTRACTS_NUMBER - (page * size))
                .isSorted(
                    compareByDescending<Contract> { it.blockNumber }
                        .then(compareByDescending<Contract> { it.txId }
                            .then(compareByDescending { it.address })
                        )
                )
        }

        @Test
        fun `get all contracts should return pagination detail`() {
            val origin = "f077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 10
            val result = mockMvc.get(
                "$BASE_ENDPOINT/$origin" +
                        "?page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val contracts = objectMapper.readValue(result.response.contentAsString, CONTRACT_RESPONSE_TYPE)

            expect {
                that(contracts.data).isNotNull().isA<List<Contract>>().hasSize(size)

                that(contracts.pagination).isNotNull()
                that(contracts.pagination!!.totalElements).isEqualTo(TOTAL_CONTRACTS_NUMBER.toLong())
                that(contracts.pagination!!.totalPages).isEqualTo(numberOfPages(size))
            }
        }

        private fun numberOfPages(size: Int) =
            (TOTAL_CONTRACTS_NUMBER / size) + (if (TOTAL_CONTRACTS_NUMBER % size > 0) 1 else 0)
    }
}