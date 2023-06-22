package org.vechain.indexer.controller

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.vechain.indexer.AbstractIntegrationTest
import org.vechain.indexer.constants.DEFAULT_PAGE_SIZE
import org.vechain.indexer.constants.TRANSACTIONS_PATH
import org.vechain.indexer.model.IndexedTransaction
import org.vechain.indexer.model.rest.COUNT_LIMIT
import org.vechain.indexer.model.rest.PAGE_SIZE_LIMIT
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.*

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
        fun `get transactions with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$baseEndpoint?origin=$address&size=$size")
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(39)
        }

        @Test
        fun `uppercase should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(39)
        }

        @Test
        fun `mixed case should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(39)
        }

        @Test
        fun `no prefix should be valid`() {
            val result = mockMvc.get(
                "$baseEndpoint?origin=f077b491b355E64048cE21E3A6Fc4751eEeA77fa" +
                        "&size=$resultsPageSize"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(39)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(40)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(39)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(DEFAULT_PAGE_SIZE)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(19)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(size)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(9)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data)
                .hasSize(9)
                .isSorted(
                    compareByDescending<IndexedTransaction> { it.blockNumber }
                        .then(compareByDescending { it.id })
                )
        }

        @Test
        fun `get txs by origin only - sorted by blockNumber & id & with pagination detail`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val includeDelegated = false
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin" +
                        "&includeDelegated=$includeDelegated" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expect {
                that(transactions.data)
                    .hasSize(39)
                    .isSorted(
                        compareByDescending<IndexedTransaction> { it.blockNumber }
                            .then(compareByDescending { it.id })
                    )

                that(transactions.pagination.hasCount).isTrue()
                that(transactions.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(transactions.pagination.totalElements).isEqualTo(39)
                that(transactions.pagination.totalPages).isEqualTo(1)
                that(transactions.pagination.hasNext).isFalse()
            }
        }

        @Test
        fun `get txs by origin or delegator - sorted by blockNumber & id & with pagination detail`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val includeDelegated = true
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result = mockMvc.get(
                "$baseEndpoint?origin=$origin" +
                        "&includeDelegated=$includeDelegated" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expect {
                that(transactions.data)
                    .hasSize(40)
                    .isSorted(
                        compareByDescending<IndexedTransaction> { it.blockNumber }
                            .then(compareByDescending { it.id })
                    )

                that(transactions.pagination.hasCount).isTrue()
                that(transactions.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(transactions.pagination.totalElements).isEqualTo(40)
                that(transactions.pagination.totalPages).isEqualTo(1)
                that(transactions.pagination.hasNext).isFalse()
            }
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
        fun `get delegated transactions with over the limit page size should return BAD REQUEST`() {
            val address = "0xf077b491b355e64048ce21e3a6fc4751eeea77fa"
            val size = PAGE_SIZE_LIMIT + 1

            mockMvc.get("$baseEndpoint/delegated?delegator=$address&size=$size")
                .andExpect { status { isBadRequest() } }
        }

        @Test
        fun `get DELEGATED transactions should return OKAY`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(1)
        }

        @Test
        fun `uppercase address should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=0xF077B491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(1)
        }

        @Test
        fun `mixed case address should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=0xF077b491B355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(1)
        }

        @Test
        fun `no hex prefix should be valid`() {
            val result = mockMvc.get("$baseEndpoint/delegated?delegator=f077b491b355E64048cE21E3A6Fc4751eEeA77fa")
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(1)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(1)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).isEmpty()
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(size)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data).hasSize(size)
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

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expectThat(transactions.data)
                .hasSize(size)
                .isSorted(
                    compareByDescending<IndexedTransaction> { it.blockNumber }
                        .then(compareByDescending { it.id })
                )
        }

        @Test
        fun `get delegated txs - sorted by blockNumber & id & with pagination detail`() {
            val origin = "0xf077b491b355E64048cE21E3A6Fc4751eEeA77fa"
            val page = 0
            val size = PAGE_SIZE_LIMIT
            val result = mockMvc.get(
                "$baseEndpoint/delegated?delegator=$origin" +
                        "&page=$page" +
                        "&size=$size"
            )
                .andExpect { status { isOk() } }
                .andReturn()

            val transactions = objectMapper.readValue(result.response.contentAsString, PAGINATED_TXS_TYPE)

            expect {
                that(transactions.data)
                    .hasSize(1)
                    .isSorted(
                        compareByDescending<IndexedTransaction> { it.blockNumber }
                            .then(compareByDescending { it.id })
                    )

                that(transactions.pagination.hasCount).isTrue()
                that(transactions.pagination.countLimit).isEqualTo(COUNT_LIMIT)
                that(transactions.pagination.totalElements).isEqualTo(1)
                that(transactions.pagination.totalPages).isEqualTo(1)
                that(transactions.pagination.hasNext).isFalse()
            }
        }
    }
}