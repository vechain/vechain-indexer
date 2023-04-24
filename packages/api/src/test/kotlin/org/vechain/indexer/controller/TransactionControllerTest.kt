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
    inner class OriginTransactions {

        // larger pagination size to get all results (page is a zero based index)
        private val resultsPageSize = 50

        @Test
        fun `get transactions for bad address should return BAD_REQUEST`() {
            mockMvc.get("$baseEndpoint/origin?address=badAddress&size=$resultsPageSize")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `valid address should return OKAY`() {
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(39)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `uppercase should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(39)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `mixed case should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(39)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `no prefix should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=f077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(39)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `include delegated should return 1 more`() {
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&includeDelegated=true" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(40)
            expectThat(transactions.pagination?.totalElements).isEqualTo(40)
        }

        @Test
        fun `include delegated false - same as regular call`() {
            val result =
                mockMvc.get(
                    "$baseEndpoint/origin?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                            "&includeDelegated=false" +
                            "&size=$resultsPageSize"
                )
                    .andExpect { status { isOk() } }
                    .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(39)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }
    }

    @Nested
    inner class PaginatedOriginTransactions {

        @Test
        fun `get txs by origin - pagination defaults`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=$origin"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(DEFAULT_PAGE_SIZE)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `get txs by origin - pagination with page only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=$origin" +
                        "&page=$page"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(19)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `get txs by origin - pagination with size only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val size = 25
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=$origin" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(size)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `get txs by origin - pagination with page & size`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 3
            val size = 10
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(9)
            expectThat(transactions.pagination?.totalElements).isEqualTo(39)
        }

        @Test
        fun `get txs by origin - sorted by blockNumber & id`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 3
            val size = 10
            val result = mockMvc.get(
                "$baseEndpoint/origin?address=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data!!)
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
            mockMvc.get("$baseEndpoint/delegated?address=badAddress")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get DELEGATED transactions should return OKAY`() {
            val result = mockMvc.get("$baseEndpoint/delegated?address=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(1)
        }

        @Test
        fun `uppercase address should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?address=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(1)
        }

        @Test
        fun `mixed case address should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?address=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(1)
        }

        @Test
        fun `no hex prefix should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?address=f077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(1)
        }
    }

    @Nested
    inner class PaginatedDelegatedTransactions {

        @Test
        fun `get delegated txs - pagination defaults`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val result = mockMvc.get(
                "$baseEndpoint/delegated?address=$origin"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(1)
        }

        @Test
        fun `get delegated txs - pagination with page only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?address=$origin" +
                        "&page=$page"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data!!).isEmpty()
        }

        @Test
        fun `get delegated txs - pagination with size only`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val size = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?address=$origin" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(size)
        }

        @Test
        fun `get delegated txs - pagination with page & size`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?address=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data?.size).isEqualTo(size)
        }

        @Test
        fun `get delegated txs - sorted by blockNumber & id`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = 1
            val result = mockMvc.get(
                "$baseEndpoint/delegated?address=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TX_TYPE)

            expectThat(transactions.data!!)
                .hasSize(size)
                .isSorted(
                    compareByDescending<Transaction> { it.blockNumber }
                        .then(compareByDescending { it.id })
                )
        }
    }
}