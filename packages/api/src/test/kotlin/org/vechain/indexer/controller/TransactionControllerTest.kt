package org.vechain.indexer.controller

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.model.Transaction
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isSorted

internal class TransactionControllerTest : AbstractIntegrationTest() {

    companion object {
        const val baseEndpoint = TRANSACTIONS_PATH
    }

    @Autowired
    lateinit var mockMvc: MockMvc

    @Nested
    inner class TransactionsById {

        @Test
        fun `get transaction by id with invalid id should return INVALID_REQUEST`() {
            mockMvc.get("$baseEndpoint/invalid_id")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get transaction by id with valid endpoint should return OK`() {
            val result =
                mockMvc.get("$baseEndpoint/0x0569d985aff6e073af33415f5ca4e848742cb483533015486dd96779c6e8251d")
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transaction = objectMapper.readValue(result.response.contentAsString, TX_TYPE)

            expectThat(transaction.id).isEqualTo("0x0569d985aff6e073af33415f5ca4e848742cb483533015486dd96779c6e8251d")
        }

        @Test
        fun `get transaction by id with transaction that doesn't exist should return NOT_FOUND`() {
            mockMvc.get("$baseEndpoint/0x00000005aff6e073af33415f5ca4e848742cb483533015486dd9000000000000")
                .andExpect { status { isNotFound() } }
        }
    }

    @Nested
    inner class OriginTransactions {

        // larger pagination size to get all results (page is a zero based index)
        private val resultsPageSize = 50

        @Test
        fun `get transactions for bad address should return BAD_REQUEST`() {
            mockMvc.get("$baseEndpoint?origin=badAddress&size=$resultsPageSize")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `valid address should return OKAY`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(39)
        }

        @Test
        fun `uppercase should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(39)
        }

        @Test
        fun `mixed case should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(39)
        }

        @Test
        fun `no prefix should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=f077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(39)
        }

        @Test
        fun `include delegated should return 1 more`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&includeDelegated=true" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(40)
        }

        @Test
        fun `include delegated false - same as regular call`() {
            val result =
                mockMvc.get(
                    "$baseEndpoint?origin=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                            "&includeDelegated=false" +
                            "&size=$resultsPageSize"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(39)
        }
    }

    @Nested
    inner class PaginatedOriginTransactions {

        @Test
        fun `get txs by origin - pagination defaults`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(DEFAULT_PAGE_SIZE)
        }

        @Test
        fun `get txs by origin - pagination with page only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin" +
                        "&page=$page"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(19)
        }

        @Test
        fun `get txs by origin - pagination with size only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val size = 25
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(size)
        }

        @Test
        fun `get txs by origin - pagination with page & size`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 3
            val size = 10
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(9)
        }

        @Test
        fun `get txs by origin - sorted by blockNumber & id`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 3
            val size = 10
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions)
                .hasSize(9)
                .isSorted(
                    compareByDescending<Transaction> { it.blockNumber }
                        .then(compareByDescending { it.id })
                )
        }
    }


    @Nested
    inner class DelegatedTransactions {

        @Test
        fun `get DELEGATED transactions for bad address should return BAD_REQUEST`() {
            mockMvc.get("$baseEndpoint/delegated?delegator=badAddress")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get DELEGATED transactions should return OKAY`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(1)
        }

        @Test
        fun `uppercase address should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(1)
        }

        @Test
        fun `mixed case address should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(1)
        }

        @Test
        fun `no hex prefix should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=f077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(1)
        }
    }

    @Nested
    inner class PaginatedDelegatedTransactions {

        @Test
        fun `get delegated txs - pagination defaults`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val result = mockMvc.get(
                "$baseEndpoint/delegated?delegator=$origin"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(1)
        }

        @Test
        fun `get delegated txs - pagination with page only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?delegator=$origin" +
                        "&page=$page"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).isEmpty()
        }

        @Test
        fun `get delegated txs - pagination with size only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val size = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?delegator=$origin" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(size)
        }

        @Test
        fun `get delegated txs - pagination with page & size`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?delegator=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions).hasSize(size)
        }

        @Test
        fun `get delegated txs - sorted by blockNumber & id`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?delegator=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, LIST_TX_TYPE)

            expectThat(transactions)
                .hasSize(size)
                .isSorted(
                    compareByDescending<Transaction> { it.blockNumber }
                        .then(compareByDescending { it.id })
                )
        }
    }
}